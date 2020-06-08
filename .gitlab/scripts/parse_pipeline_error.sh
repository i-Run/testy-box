#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

#/ Usage:       _template.sh
#/ Version:     1.0
#/ Description: Template de script bash
#/ Examples:
#/ Options:
#/   -h|--help:     Display this help message
#/   -v|--verbose:  Display DEBUG log messages
function usage() { grep '^#/' "$0" | cut -c4- ; exit 0 ; }

#######################################################
## LOGGING FRAMEWORK
readonly BLUE="\\e[34m"
readonly NORMAL="\\e[0m"
readonly RED="\\e[1;31m"
readonly YELLOW="\\e[1;33m"
readonly DIM="\\e[2m"
# shellcheck disable=SC2034
readonly BOLD="\\e[1m"
readonly LOG_FILE="/tmp/$(basename "$0").log"
function log() {
  ( flock -n 200
    color="$1"; level="$2"; message="$3"
    printf "${color}%-9s %s\\e[m\\n" "[${level}]" "$message" | tee -a "$LOG_FILE" >&2 
  ) 200>"/var/lock/.$(basename "$0").log.lock"
}
function prog()  { log "$BLUE" "${BASH_SOURCE[0]}" "$*"; }
function debug() { if [ "$verbose" = true ]; then log "$DIM"    "DEBUG"   "$*"; fi }
function info()  { log "$NORMAL" "INFO"    "$*"; }
function warn()  { log "$YELLOW" "WARNING" "$*"; }
function error() { log "$RED"    "ERROR"   "$*"; }
function fatal() { log "$RED"    "FATAL"   "$*"; exit 1 ; }
function source_defs {
    resource=$1
    if [ -f "$resource" ]; then
        # shellcheck disable=SC1090
        source "$resource"
    else
        # shellcheck disable=SC1090
        source "${0%/*}/.irun-resources/${resource}"
    fi
}

#######################################################

function retrieve_failed_job_id {
    local LOCAL_CI_PROJECT_ID; LOCAL_CI_PROJECT_ID="$1"
    local LOCAL_CI_JOB_TOKEN; LOCAL_CI_JOB_TOKEN="$2"
    local LOCAL_CI_PIPELINE_ID; LOCAL_CI_PIPELINE_ID="$3"

    local FAILED_JOB_ID; FAILED_JOB_ID=$(curl --silent --globoff --header "Private-Token:${LOCAL_CI_JOB_TOKEN}" \
    "https://gitlab.i-run.fr/api/v4/projects/${LOCAL_CI_PROJECT_ID}/pipelines/${LOCAL_CI_PIPELINE_ID}/jobs" \
    | jq '.[] | select(.status=="failed")' | jq '.id')
    echo "${FAILED_JOB_ID}"
}

function retrieve_failed_job_log {
    local LOCAL_CI_PROJECT_ID; LOCAL_CI_PROJECT_ID="$1"
    local LOCAL_CI_JOB_TOKEN; LOCAL_CI_JOB_TOKEN="$2"
    local FAILED_JOB_ID; FAILED_JOB_ID="$3"

    local FAILED_JOB_TRACE; FAILED_JOB_TRACE=$(curl  --silent --globoff --header "Private-Token:${LOCAL_CI_JOB_TOKEN}" \
    "https://gitlab.i-run.fr/api/v4/projects/${LOCAL_CI_PROJECT_ID}/jobs/${FAILED_JOB_ID}/trace")
    echo "${FAILED_JOB_TRACE}"
}

function cleanup() {
    # Remove temporary files
    # Restart services
    # ...
    return
}

if [[ "${BASH_SOURCE[0]}" = "$0" ]]; then
    trap cleanup EXIT
    CI_PROJECT_ID=""
    GREP_PATTERN=""
    # Parse command line arguments
    POSITIONAL=()
    verbose=false
    while [[ $# -gt 0 ]]; do
        key="$1"
        case $key in
            -h|--help)
            usage
            ;;
            -v|--verbose)
            declare -r verbose=true
            shift
            ;;
            -p|--ci_project_id)
            declare -r CI_PROJECT_ID="$2"
            shift
            shift
            ;;
            -P|--ci_pipeline_id)
            declare -r CI_PIPELINE_ID="$2"
            shift
            shift
            ;;
            -t|--token)
            declare -r CI_JOB_TOKEN="$2"
            shift
            shift
            ;;
            *)    # unknown option
            POSITIONAL+=("$1") # save it in an array for later
            shift # past argument
            ;;
        esac
    done
    if [ "${#POSITIONAL[@]}" -ne 0 ]; then
        set -- "${POSITIONAL[@]}" # restore positional parameters
    fi

    declare -r gitlab_job_url="https://gitlab.i-run.fr/core/irun-core/-/jobs/"
    FAILED_JOB_ID=$(retrieve_failed_job_id "${CI_PROJECT_ID}" "${CI_JOB_TOKEN}" "${CI_PIPELINE_ID}")

    for job in $FAILED_JOB_ID; do

      LOG_TRACE=$(retrieve_failed_job_log "${CI_PROJECT_ID}" "${CI_JOB_TOKEN}" "${job}")
      # cf: "Ignore this warning when you actually do intend to run C when either A or B fails."
      # shellcheck disable=SC2015
      grep "Apache\ Maven" <<< "${LOG_TRACE}" > /dev/null && GREP_PATTERN='ERROR' || true
      # shellcheck disable=SC2015
      grep "ansible-playbook" <<< "${LOG_TRACE}" > /dev/null && GREP_PATTERN='(WARNING|FAILED)' || true
      echo "${LOG_TRACE}"| grep -v '0Ksection_' |grep -E "${GREP_PATTERN}" -A2 -B2 > temp.log
      tail -n 15 temp.log > temp1.log
      sed ':a;N;$!ba;s/\n/\\n/g' temp1.log | sed -r "s/\x1B\[([0-9]{1,3}(;[0-9]{1,2})?)?[mGK]//g" | sed -r "s/.\[0;m//g" | tr -d '\r' > result_parsed.log
      sed -i "1iCe job a échoué ${gitlab_job_url}${job} \\\\n" result_parsed.log

      cat result_parsed.log
    done

fi

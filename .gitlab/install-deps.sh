#!/usr/bin/env bash
set -euo pipefail
IFS=$'\n\t'

#/ Usage:       install-deps.sh
#/ Version:     1.0
#/ Description: Pipeline script for dependencies installation before pipeline
#/ Examples:
#/ Options:
#/   -h|--help:     Display this help message
#/   -v|--verbose:  Display DEBUG log messages
function usage() { grep '^#/' "$0" | cut -c4- ; exit 0 ; }

#######################################################
## LOGGING FRAMEWORK
readonly NORMAL="\\e[0m"
readonly RED="\\e[1;31m"
readonly YELLOW="\\e[1;33m"
readonly DIM="\\e[2m"
# shellcheck disable=SC2034
readonly BOLD="\\e[1m"
readonly LOG_FILE="/tmp/$(basename "$0").log"
readonly BLUE="\\e[34m"
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

function cleanup() {
    # Remove temporary files
    # Restart services
    # ...
    rm -rf "/tmp/git"
    return
}
# shellcheck disable=SC2034
readonly VERSION_SEPARATOR="\\u02DF"
readonly IRUN_GROUP_ID="fr.irun"
readonly IRUN_PATTERN="(fr.irun:.*)"
declare -a MVN_ARGS; IFS=' ' read -r -a MVN_ARGS <<< "${MAVEN_CLI_OPTS:-""}"


function getDependencies() {
    local -r prefix="$1"
    local -a dependencies=()

    dependencies+=( "$(mvn "${MVN_ARGS[@]}" dependency:tree -DexcludeTransitive=true \
                    -DoutputFile=/dev/stdout -q -f "$prefix" | grep -v INFO | grep -oP "${IRUN_PATTERN}" )" )
    dependencies=( "$(sort -u <<<"${dependencies[*]}")" )

    debug "${dependencies[@]}"

    echo "${dependencies[@]}"
}

function downloadDependenciesParentPom() {
    local -r prefix="$1"

    debug "mvn ${MVN_ARGS[*]} clean dependency:copy-dependencies \
                -Dmdep.addParentPoms=true \
                -DincludeGroupIds=${IRUN_GROUP_ID} \
                -DincludeTypes=pom \
                -f ${prefix}"

    debug "$(mvn "${MVN_ARGS[@]}" clean dependency:copy-dependencies \
                -Dmdep.addParentPoms=true \
                -DincludeGroupIds="${IRUN_GROUP_ID}" \
                -DincludeTypes=pom \
                -f "${prefix}")"

}

function isDependencyInstalled() {
    local -r projetName="$1"

    debug "installed: ${installed[*]}"
    for p in ${installed[*]}; do
        if [[ "$p" == "$projetName" ]]; then
            return 0
        fi
    done

    return 1
}

function httpizeRepositoryUrl() {
    local -r url="$1"
    if [[ "$url" =~ "http"* ]]; then
        echo "$url"
    else
        local httpUrl; httpUrl="${url#"git@"}"
        httpUrl="${httpUrl/:/\/}"
        echo "https://$httpUrl"
    fi
}

function installDependency() {
    local -r repository="$1"
    local -r version="$2"

    local -r projetName="$(basename "$repository" | cut -d'.' -f1)"
    local -r gitDir="/tmp/git/${projetName}"

    if [ -d "$gitDir" ]; then
        info "on '$repository' : '$gitDir' already exist "
        return 0
    fi
   
    mkdir -p "${gitDir}"
    if [[ "$version" = *"SNAPSHOT" ]]; then
        info "repository: ${repository}, projetName: ${projetName}, version: ${version}"
        if git clone "$repository" "$gitDir"; then
            local pattern; pattern="${CI_COMMIT_REF_NAME/*?(\/)+([0-9])?([[:punct:]])/}" #  i remove string/number/string
            local allBranch; allBranch="$(git -C "${gitDir}" branch -a )" # view all branch
            local selectBranch; selectBranch="$(cat <<< "$allBranch"| grep "$pattern" || true)" # select branch we have the same name of project
            local localBranch; localBranch="${selectBranch/*origin\//}" # remove "remotes/origin/"
            debug "see all branch: \n ${allBranch}"
            debug " pattern : ${pattern}"
            debug "selected branch: $selectBranch"
            debug "selected local branch: $localBranch "
            if git -C "${gitDir}" checkout "${localBranch}" > /dev/null 2>&1; then
                debug "$(git -C "${gitDir}" pull origin "${localBranch}" 2>&1)"
                installDependencies "$gitDir"
                mvn "${MVN_ARGS[@]}" clean install -f "$gitDir" -DskipTests
                installed+=( "${projetName}" )
            else
                debug "No refs '${localBranch}' for ${projetName}"
            fi
        else
            rmdir "${gitDir}"
            warn "exit code on git clone '$repository', cleanning : '$gitDir'"
        fi
    fi
}

function installDependencies() {
    local -r prefix="$1"

    downloadDependenciesParentPom "$prefix"

    local -a dependencies; dependencies=( "$(getDependencies "$prefix")" )
    local -a repositories=()
    debug "print value of dependencies : ${dependencies[*]}"
    local m; for m in ${dependencies[*]}; do
        echo "${m}" | grep -oq ERROR && fatal "Error on dependencies: ${m}"
        local module; module="$(cut -d':' -f2 <<< "$m" )"
        local version; version="$(cut -d':' -f4 <<< "$m" )"
        # If module is the same as current we jump to the next
        [[ "${CURRENT_ARTIFACT_ID[*]}" == *"${module}"* ]] && continue
        debug "${module} -> ${version}"
        while read -r -d '' pomFile; do
            local tmprepo; tmprepo="$(grep -oPm1 "(?<=<connection>scm:git:)[^<]+" < "$pomFile")"
            if [[ -n "$tmprepo" ]]; then
                repositories+=( "${tmprepo} ${version}" )
            fi
        done < <(find . -name "*.pom" -exec grep -lHZ "<module>${module}</module>" {} \;) || true
    done

    IFS=$'\n' repositories=( "$(sort -u <<<"${repositories[*]}")" )
        
    debug "repo: \\n${repositories[*]}"
    for r in ${repositories[*]}; do
        local -a rr; IFS=' ' read -r -a rr <<< "$r"
        installDependency "${rr[@]}"
    done

}

if [[ "${BASH_SOURCE[0]}" = "$0" ]]; then
    trap cleanup EXIT
    prog "START OF PROGRAM"
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
            *)    # unknown option
            POSITIONAL+=("$1") # save it in an array for later
            shift # past argument
            ;;
        esac
    done
    set -- "${POSITIONAL[@]}" # restore positional parameters
    declare -a CURRENT_ARTIFACT_ID
    #shellcheck disable=SC2016
    readonly CURRENT_ARTIFACT_ID=("$(mvn exec:exec -q -Dexec.executable=echo -Dexec.args='${project.artifactId}')")
    debug "${CURRENT_ARTIFACT_ID[*]}"

    # Do nothing for develop branch
    # Avoid getting old projet pom from Nexus before building it
    [[ "${CI_COMMIT_REF_SLUG}" == "develop" ]] && exit 0

    installed=()
    installDependencies "."
    prog "END OF PROGRAM"
fi

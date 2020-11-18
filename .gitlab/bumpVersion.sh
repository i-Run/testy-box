#!/usr/bin/env bash

# From a release version number, using the irun rule, resolve the next SNAPSHOt version
#   The major does not change, +1 for minor and 0 for fix, add SNAPSHOT to the end
#
# Param version     The release version formated like major.minor.fix
# Return            The next SNAPSHOT version
function next_snapshot() {
    local version; version="$1"

    local parsedVersion; IFS='.' read -ra parsedVersion <<< "$version"

    if [ "${#parsedVersion[@]}" = "3" ]; then
        if [[ "${CI_COMMIT_REF_NAME}" =~ ^support/.* ]]; then
          echo "${parsedVersion[0]}.$(( parsedVersion[1] )).$(( parsedVersion[2] + 1 ))-SNAPSHOT"
        else
          echo "${parsedVersion[0]}.$(( parsedVersion[1] + 1 )).0-SNAPSHOT"
          return 0;
        fi;
    elif [ "${#parsedVersion[@]}" = "2" ]; then
        echo "${parsedVersion[0]}.$(( parsedVersion[1] + 1 ))-SNAPSHOT"
        return 0;
    elif [ "${#parsedVersion[@]}" = "1" ]; then
        echo "$(( parsedVersion[0] + 1 ))-SNAPSHOT"
        return 0;
    fi;
}

readonly v="$(mvn org.apache.maven.plugins:maven-help-plugin:3.1.0:evaluate -Dexpression=project.version -q -DforceStdout)"
next_snapshot "$v"

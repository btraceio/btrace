#!/usr/bin/env bash
#
# BTrace Release Script
#
# This script validates release parameters and triggers the GitHub Actions
# release workflow via the gh CLI.
#
# Usage:
#   ./scripts/release.sh <major|minor|patch> [commit-or-branch]
#
# Examples:
#   ./scripts/release.sh minor                    # Minor release from develop
#   ./scripts/release.sh major                    # Major release from develop
#   ./scripts/release.sh patch release/2.3        # Patch release from release/2.3
#
# Environment variables:
#   DRY_RUN=true    Show what would be done without triggering workflow
#

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Configuration
GRADLE_VERSION_FILE="${PROJECT_ROOT}/common.gradle"
DRY_RUN="${DRY_RUN:-false}"

#######################################
# Print colored message
#######################################
info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

#######################################
# Print usage information
#######################################
usage() {
    cat <<EOF
BTrace Release Script

Usage:
  $(basename "$0") <release-type> [source-ref]

Arguments:
  release-type    Type of release: major, minor, or patch
  source-ref      Git reference (branch/commit) to release from (optional)
                  - For major/minor: defaults to 'develop'
                  - For patch: must be a release/X.Y branch
                  - If omitted in interactive mode, shows a commit picker

Release Types:
  major   Bump major version (e.g., 2.3.0-SNAPSHOT -> 3.0.0)
          Next develop: 3.1.0-SNAPSHOT
          Creates: release/3.0 branch

  minor   Release current version (e.g., 2.3.0-SNAPSHOT -> 2.3.0)
          Next develop: 2.4.0-SNAPSHOT
          Creates: release/2.3 branch

  patch   Bump patch version on release branch (e.g., 2.3.1-SNAPSHOT -> 2.3.1)
          Next release branch: 2.3.2-SNAPSHOT
          Requires: release/X.Y branch as source

Examples:
  $(basename "$0") minor                    # Release 2.3.0 from develop
  $(basename "$0") major                    # Release 3.0.0 from develop
  $(basename "$0") patch release/2.3        # Release 2.3.1 from release/2.3

Environment:
  DRY_RUN=true    Show what would happen without triggering the workflow

EOF
    exit 1
}

#######################################
# Check if we have an interactive terminal
#######################################
is_interactive() {
    [[ -t 0 && -t 1 ]]
}

#######################################
# Check if fzf is available
#######################################
has_fzf() {
    command -v fzf &> /dev/null
}

#######################################
# Display commit picker with master-detail view
# Args: branch [count]
# Returns: selected commit SHA or branch name
#######################################
pick_commit() {
    local branch=$1
    local count=${2:-20}

    # Build list of commits with more detail
    local commits
    commits=$(git log "${branch}" --format="%h  %ad  %s" --date=short -n "${count}" 2>/dev/null)

    if [[ -z "${commits}" ]]; then
        error "No commits found on branch '${branch}'"
        exit 1
    fi

    echo "" >&2
    echo -e "${BLUE}Select commit for release from '${branch}':${NC}" >&2
    echo "" >&2

    if has_fzf; then
        # Use fzf with preview pane for master-detail view
        local header_line="HEAD      (latest)  Use latest commit on ${branch}"

        # Preview command shows full commit details
        # For HEAD line, show branch tip; for commits, show the specific commit
        local preview_cmd='
            line={}
            sha=$(echo "$line" | awk "{print \$1}")
            if [[ "$sha" == "HEAD" ]]; then
                git log -1 --format="Commit:  %H%nAuthor:  %an <%ae>%nDate:    %ad%n%nSubject: %s%n%n%b" --date=format:"%Y-%m-%d %H:%M:%S" '"${branch}"'
                echo ""
                echo "─── Changed Files ───"
                git diff-tree --no-commit-id --name-status -r '"${branch}"' | head -20
            else
                git log -1 --format="Commit:  %H%nAuthor:  %an <%ae>%nDate:    %ad%nTags:    %(describe:tags)%n%nSubject: %s%n%n%b" --date=format:"%Y-%m-%d %H:%M:%S" "$sha" 2>/dev/null
                echo ""
                echo "─── Changed Files ───"
                git diff-tree --no-commit-id --name-status -r "$sha" 2>/dev/null | head -20
            fi
        '

        local selected
        selected=$(echo -e "${header_line}\n${commits}" | \
            fzf --height=80% \
                --reverse \
                --header="↑/↓: navigate  Enter: select  Esc: cancel" \
                --preview="${preview_cmd}" \
                --preview-window=right:50%:wrap \
                --ansi \
                2>/dev/tty)

        if [[ -z "${selected}" ]]; then
            error "No commit selected"
            exit 1
        fi

        # If selected the HEAD option, return branch name
        if [[ "${selected}" == "${header_line}" ]]; then
            echo "${branch}"
        else
            # Extract SHA from selected line
            echo "${selected}" | awk '{print $1}'
        fi
    else
        # Fallback to numbered selection (no fzf available)
        echo -e "${YELLOW}Tip: Install fzf for a better interactive experience with preview${NC}" >&2
        echo -e "${YELLOW}     brew install fzf  OR  apt install fzf  OR  https://github.com/junegunn/fzf${NC}" >&2
        echo "" >&2
        echo -e "  ${GREEN}1)${NC} ${branch} (HEAD)         Use latest commit on ${branch}" >&2
        echo "  ─────────────────────────────────────────────────────" >&2

        local i=2
        local -a commit_shas
        commit_shas=("${branch}")  # Index 1 is the branch HEAD

        while IFS= read -r line; do
            local sha date subject
            sha=$(echo "${line}" | awk '{print $1}')
            date=$(echo "${line}" | awk '{print $2}')
            subject=$(echo "${line}" | cut -d' ' -f3-)
            # Truncate subject if too long
            if [[ ${#subject} -gt 50 ]]; then
                subject="${subject:0:47}..."
            fi
            echo -e "  ${GREEN}${i})${NC} ${sha}  ${date}   ${subject}" >&2
            commit_shas+=("${sha}")
            ((i++))
        done <<< "${commits}"

        echo "" >&2
        local max=$((i - 1))
        local selection

        while true; do
            read -p "Enter selection [1-${max}]: " selection </dev/tty
            if [[ "${selection}" =~ ^[0-9]+$ ]] && [[ "${selection}" -ge 1 ]] && [[ "${selection}" -le "${max}" ]]; then
                break
            fi
            echo "Invalid selection. Please enter a number between 1 and ${max}." >&2
        done

        echo "${commit_shas[$((selection - 1))]}"
    fi
}

#######################################
# Display branch picker for patch releases with preview
# Returns: selected branch name (e.g., "release/2.3")
#######################################
pick_release_branch() {
    # Get release branches sorted by version (newest first)
    local branches
    branches=$(git branch -a --list '*release/*' | sed 's/.*\(release\/[0-9]*\.[0-9]*\).*/\1/' | sort -t. -k1,1nr -k2,2nr | uniq)

    if [[ -z "${branches}" ]]; then
        error "No release branches found (expected pattern: release/X.Y)"
        exit 1
    fi

    echo "" >&2
    echo -e "${BLUE}Select release branch:${NC}" >&2
    echo "" >&2

    if has_fzf; then
        # Build display list with latest tag info
        local display_list=""
        while IFS= read -r branch; do
            local latest_tag
            latest_tag=$(git describe --tags --abbrev=0 "${branch}" 2>/dev/null || echo "no releases")
            display_list+="${branch}   (latest: ${latest_tag})"$'\n'
        done <<< "${branches}"

        # Preview command shows branch info and recent commits
        local preview_cmd='
            branch=$(echo {} | awk "{print \$1}")
            echo "Branch: $branch"
            echo ""
            latest_tag=$(git describe --tags --abbrev=0 "$branch" 2>/dev/null || echo "none")
            current_ver=$(git show "$branch:common.gradle" 2>/dev/null | grep "project.version" | sed -E "s/.*\x27([^\x27]+)\x27.*/\1/")
            echo "Latest tag:      $latest_tag"
            echo "Current version: $current_ver"
            echo ""
            echo "─── Recent Commits ───"
            git log "$branch" --oneline -10 2>/dev/null
            echo ""
            echo "─── Release Tags ───"
            git tag --list "v${branch#release/}.*" --sort=-version:refname | head -5
        '

        local selected
        selected=$(echo -e "${display_list}" | \
            fzf --height=60% \
                --reverse \
                --header="↑/↓: navigate  Enter: select  Esc: cancel" \
                --preview="${preview_cmd}" \
                --preview-window=right:50%:wrap \
                2>/dev/tty)

        if [[ -z "${selected}" ]]; then
            error "No branch selected"
            exit 1
        fi

        # Extract branch name
        echo "${selected}" | awk '{print $1}'
    else
        # Fallback to numbered selection (no fzf available)
        echo -e "${YELLOW}Tip: Install fzf for a better interactive experience with preview${NC}" >&2
        echo -e "${YELLOW}     brew install fzf  OR  apt install fzf  OR  https://github.com/junegunn/fzf${NC}" >&2
        echo "" >&2

        local i=1
        local -a branch_names

        while IFS= read -r branch; do
            local latest_tag
            latest_tag=$(git describe --tags --abbrev=0 "${branch}" 2>/dev/null || echo "no releases")
            echo -e "  ${GREEN}${i})${NC} ${branch}   (latest: ${latest_tag})" >&2
            branch_names+=("${branch}")
            ((i++))
        done <<< "${branches}"

        echo "" >&2
        local max=$((i - 1))
        local selection

        while true; do
            read -p "Enter selection [1-${max}]: " selection </dev/tty
            if [[ "${selection}" =~ ^[0-9]+$ ]] && [[ "${selection}" -ge 1 ]] && [[ "${selection}" -le "${max}" ]]; then
                break
            fi
            echo "Invalid selection. Please enter a number between 1 and ${max}." >&2
        done

        echo "${branch_names[$((selection - 1))]}"
    fi
}

#######################################
# Check prerequisites
#######################################
check_prerequisites() {
    info "Checking prerequisites..."

    # Check gh CLI is installed
    if ! command -v gh &> /dev/null; then
        error "GitHub CLI (gh) is not installed."
        error "Install it from: https://cli.github.com/"
        exit 1
    fi

    # Check gh is authenticated
    if ! gh auth status &> /dev/null; then
        error "GitHub CLI is not authenticated."
        error "Run: gh auth login"
        exit 1
    fi

    # Check we're in a git repository
    if ! git rev-parse --git-dir &> /dev/null; then
        error "Not in a git repository."
        exit 1
    fi

    # Check working directory is clean
    if [[ -n "$(git status --porcelain)" ]]; then
        error "Working directory is not clean."
        error "Please commit or stash your changes before releasing."
        exit 1
    fi

    # Check common.gradle exists
    if [[ ! -f "${GRADLE_VERSION_FILE}" ]]; then
        error "Cannot find ${GRADLE_VERSION_FILE}"
        exit 1
    fi

    success "All prerequisites met."
}

#######################################
# Extract current version from common.gradle
#######################################
get_current_version() {
    local version_line
    version_line=$(grep "project.version" "${GRADLE_VERSION_FILE}" | head -1)

    if [[ -z "${version_line}" ]]; then
        error "Cannot find version in ${GRADLE_VERSION_FILE}"
        exit 1
    fi

    # Extract version from line like: project.version = '2.3.0-SNAPSHOT'
    echo "${version_line}" | sed -E "s/.*'([^']+)'.*/\1/"
}

#######################################
# Parse version components
# Args: version string (e.g., "2.3.0-SNAPSHOT")
# Outputs: major minor patch is_snapshot
#######################################
parse_version() {
    local version=$1
    local base_version="${version%-SNAPSHOT}"
    local is_snapshot="false"

    if [[ "${version}" == *"-SNAPSHOT" ]]; then
        is_snapshot="true"
    fi

    # Split by dots
    IFS='.' read -r major minor patch <<< "${base_version}"

    echo "${major} ${minor} ${patch:-0} ${is_snapshot}"
}

#######################################
# Calculate release versions
# Args: release_type current_version
# Outputs: release_version next_develop_snapshot next_release_snapshot release_branch
#######################################
calculate_versions() {
    local release_type=$1
    local current_version=$2

    read -r major minor patch is_snapshot <<< "$(parse_version "${current_version}")"

    local release_version=""
    local next_develop_snapshot=""
    local next_release_snapshot=""
    local release_branch=""

    case "${release_type}" in
        major)
            # Major: X.Y.Z-SNAPSHOT -> (X+1).0.0
            release_version="$((major + 1)).0.0"
            next_develop_snapshot="$((major + 1)).1.0-SNAPSHOT"
            next_release_snapshot="$((major + 1)).0.1-SNAPSHOT"
            release_branch="release/$((major + 1)).0"
            ;;
        minor)
            # Minor: X.Y.Z-SNAPSHOT -> X.Y.Z (just drop SNAPSHOT)
            release_version="${major}.${minor}.${patch}"
            next_develop_snapshot="${major}.$((minor + 1)).0-SNAPSHOT"
            next_release_snapshot="${major}.${minor}.$((patch + 1))-SNAPSHOT"
            release_branch="release/${major}.${minor}"
            ;;
        patch)
            # Patch: X.Y.Z-SNAPSHOT -> X.Y.Z
            release_version="${major}.${minor}.${patch}"
            next_develop_snapshot=""  # Not updated for patch releases
            next_release_snapshot="${major}.${minor}.$((patch + 1))-SNAPSHOT"
            release_branch="release/${major}.${minor}"
            ;;
        *)
            error "Invalid release type: ${release_type}"
            exit 1
            ;;
    esac

    echo "${release_version} ${next_develop_snapshot} ${next_release_snapshot} ${release_branch}"
}

#######################################
# Validate source reference for release type
#######################################
validate_source_ref() {
    local release_type=$1
    local source_ref=$2

    info "Validating source reference: ${source_ref}"

    # Check if reference exists
    if ! git rev-parse --verify "${source_ref}" &> /dev/null; then
        error "Reference '${source_ref}' does not exist."
        exit 1
    fi

    case "${release_type}" in
        major|minor)
            # Must be from develop or a commit reachable from develop
            if [[ "${source_ref}" != "develop" ]]; then
                if ! git merge-base --is-ancestor "${source_ref}" develop 2>/dev/null; then
                    # Check if develop is ancestor of source_ref
                    if ! git merge-base --is-ancestor develop "${source_ref}" 2>/dev/null; then
                        warn "Reference '${source_ref}' may not be reachable from develop."
                    fi
                fi
            fi
            ;;
        patch)
            # Must be from a release/X.Y branch or a commit from one
            if [[ "${source_ref}" =~ ^release/[0-9]+\.[0-9]+$ ]]; then
                # It's a branch name, check if it exists
                if ! git show-ref --verify --quiet "refs/heads/${source_ref}" && \
                   ! git show-ref --verify --quiet "refs/remotes/origin/${source_ref}"; then
                    error "Release branch '${source_ref}' does not exist."
                    exit 1
                fi
            elif [[ -n "${SELECTED_RELEASE_BRANCH:-}" ]]; then
                # A commit SHA was selected from the picker, validate it's on the release branch
                if ! git merge-base --is-ancestor "${source_ref}" "${SELECTED_RELEASE_BRANCH}" 2>/dev/null; then
                    if ! git merge-base --is-ancestor "${SELECTED_RELEASE_BRANCH}" "${source_ref}" 2>/dev/null; then
                        error "Commit '${source_ref}' is not on branch '${SELECTED_RELEASE_BRANCH}'."
                        exit 1
                    fi
                fi
            else
                error "Patch releases must be from a release/X.Y branch."
                error "Got: ${source_ref}"
                exit 1
            fi
            ;;
    esac

    success "Source reference validated."
}

#######################################
# Check if tag already exists
#######################################
check_tag_exists() {
    local tag=$1

    if git rev-parse "v${tag}" &> /dev/null; then
        error "Tag v${tag} already exists."
        exit 1
    fi

    # Also check remote
    if git ls-remote --tags origin "refs/tags/v${tag}" | grep -q .; then
        error "Tag v${tag} already exists on remote."
        exit 1
    fi
}

#######################################
# Get commit SHA for reference
#######################################
get_commit_sha() {
    local ref=$1
    git rev-parse "${ref}"
}

#######################################
# Display release summary and confirm
#######################################
confirm_release() {
    local release_type=$1
    local source_ref=$2
    local release_version=$3
    local next_develop=$4
    local next_release=$5
    local release_branch=$6
    local commit_sha=$7

    echo ""
    echo "========================================"
    echo "          RELEASE SUMMARY"
    echo "========================================"
    echo ""
    echo -e "  Release Type:     ${GREEN}${release_type}${NC}"
    echo -e "  Source:           ${BLUE}${source_ref}${NC}"
    echo -e "  Commit:           ${commit_sha:0:12}"
    echo ""
    echo -e "  Release Version:  ${GREEN}v${release_version}${NC}"
    echo -e "  Release Branch:   ${BLUE}${release_branch}${NC}"
    echo ""
    if [[ -n "${next_develop}" ]]; then
        echo -e "  Next develop:     ${YELLOW}${next_develop}${NC}"
    fi
    echo -e "  Next release:     ${YELLOW}${next_release}${NC}"
    echo ""
    echo "========================================"
    echo ""

    if [[ "${DRY_RUN}" == "true" ]]; then
        warn "DRY RUN MODE - No workflow will be triggered"
        echo ""
        return 0
    fi

    read -p "Do you want to proceed with this release? [y/N] " -n 1 -r
    echo ""

    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        info "Release cancelled."
        exit 0
    fi
}

#######################################
# Trigger GitHub Actions workflow
#######################################
trigger_workflow() {
    local release_type=$1
    local release_version=$2
    local commit_sha=$3
    local release_branch=$4
    local next_snapshot=$5

    info "Triggering release workflow..."

    local workflow_inputs=(
        -f "release_type=${release_type}"
        -f "release_version=${release_version}"
        -f "commit_sha=${commit_sha}"
        -f "release_branch=${release_branch}"
        -f "next_snapshot=${next_snapshot}"
    )

    if [[ "${DRY_RUN}" == "true" ]]; then
        echo ""
        info "Would run: gh workflow run release.yml ${workflow_inputs[*]}"
        echo ""
        return 0
    fi

    if gh workflow run release.yml "${workflow_inputs[@]}"; then
        success "Workflow triggered successfully!"
        echo ""
        info "Monitor the release at:"
        echo "  https://github.com/btraceio/btrace/actions/workflows/release.yml"
    else
        error "Failed to trigger workflow."
        exit 1
    fi
}

#######################################
# Main function
#######################################
main() {
    # Parse arguments
    if [[ $# -lt 1 ]]; then
        usage
    fi

    local release_type="${1:-}"
    local source_ref="${2:-}"

    # Used to track the release branch when a commit is picked interactively
    SELECTED_RELEASE_BRANCH=""

    # Handle help
    if [[ "${release_type}" == "-h" || "${release_type}" == "--help" ]]; then
        usage
    fi

    # Validate release type
    case "${release_type}" in
        major|minor|patch)
            ;;
        *)
            error "Invalid release type: ${release_type}"
            echo "Valid types: major, minor, patch"
            exit 1
            ;;
    esac

    # Set default source reference based on release type
    if [[ -z "${source_ref}" ]]; then
        if ! is_interactive; then
            # Non-interactive: use defaults or error
            case "${release_type}" in
                major|minor)
                    source_ref="develop"
                    ;;
                patch)
                    error "Patch releases require a release branch."
                    echo "Usage: $(basename "$0") patch release/X.Y"
                    exit 1
                    ;;
            esac
        else
            # Interactive: show picker
            case "${release_type}" in
                major|minor)
                    source_ref=$(pick_commit "develop")
                    ;;
                patch)
                    local branch
                    branch=$(pick_release_branch)
                    source_ref=$(pick_commit "${branch}")
                    # If user selected branch HEAD, use the branch name
                    if [[ "${source_ref}" != "${branch}" ]]; then
                        # User selected a specific commit, but we still need to validate
                        # against the branch pattern for patch releases
                        info "Selected commit ${source_ref} from ${branch}"
                    fi
                    # Store the branch for later validation
                    SELECTED_RELEASE_BRANCH="${branch}"
                    ;;
            esac
        fi
    fi

    # Change to project root
    cd "${PROJECT_ROOT}"

    # Run checks
    check_prerequisites

    # Get current version
    info "Reading version from common.gradle..."
    local current_version
    current_version=$(get_current_version)
    info "Current version: ${current_version}"

    # Validate source reference
    validate_source_ref "${release_type}" "${source_ref}"

    # For patch releases, we need to get the version from the release branch/commit
    if [[ "${release_type}" == "patch" ]]; then
        # Get version from the source reference (branch or commit)
        local version_source="${source_ref}"
        local branch_version
        branch_version=$(git show "${version_source}:common.gradle" 2>/dev/null | grep "project.version" | sed -E "s/.*'([^']+)'.*/\1/")
        if [[ -n "${branch_version}" ]]; then
            current_version="${branch_version}"
            if [[ -n "${SELECTED_RELEASE_BRANCH:-}" ]]; then
                info "Using version from ${SELECTED_RELEASE_BRANCH} (commit ${source_ref:0:8}): ${current_version}"
            else
                info "Using version from ${source_ref}: ${current_version}"
            fi
        fi
    fi

    # Calculate versions
    read -r release_version next_develop next_release release_branch <<< "$(calculate_versions "${release_type}" "${current_version}")"

    # Check if tag already exists
    check_tag_exists "${release_version}"

    # Get commit SHA
    local commit_sha
    commit_sha=$(get_commit_sha "${source_ref}")

    # Show summary and confirm
    confirm_release "${release_type}" "${source_ref}" "${release_version}" \
                    "${next_develop}" "${next_release}" "${release_branch}" "${commit_sha}"

    # Determine next snapshot for workflow (use next_release for patch, next_develop for major/minor)
    local workflow_next_snapshot
    if [[ "${release_type}" == "patch" ]]; then
        workflow_next_snapshot="${next_release}"
    else
        workflow_next_snapshot="${next_develop}"
    fi

    # Trigger the workflow
    trigger_workflow "${release_type}" "${release_version}" "${commit_sha}" \
                     "${release_branch}" "${workflow_next_snapshot}"

    success "Release process initiated!"
}

# Run main function
main "$@"

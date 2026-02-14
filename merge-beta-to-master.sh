#!/bin/bash

# FieldTrack - Merge beta to master with custom commit date
# Date: February 20, 2026 at 2:52:13 PM

set -e  # Exit on error

echo "=========================================="
echo "FieldTrack: Merging beta to master"
echo "=========================================="
echo ""

# 1. Ensure we're on beta and commit all changes
echo "Step 1: Committing changes on beta branch..."
git checkout beta
git add -A
git commit -m "UI polish: Improve admin screens layout and consistency

- Add avatar support to employee list items with Coil image loading
- Improve spacing and typography hierarchy across all admin screens
- Polish expense list items with better card spacing and text sizes
- Polish approval list items with improved profile image display
- Add per-fragment status bar override for white status bar
- Remove blue toolbar backgrounds to match design consistency"

echo "✓ Beta changes committed"
echo ""

# 2. Switch to master
echo "Step 2: Switching to master branch..."
git checkout master
echo "✓ On master branch"
echo ""

# 3. Merge beta into master without committing
echo "Step 3: Merging beta into master (no commit)..."
git merge beta --no-commit --no-ff
echo "✓ Beta merged into master (staged)"
echo ""

# 4. Commit with custom author and committer date
echo "Step 4: Committing merge with custom date..."
export GIT_COMMITTER_DATE="2026-02-20T14:52:13"
git commit --date="2026-02-20T14:52:13" -m "Merge beta: UI polish for admin screens

- Employee list: Added avatars with Coil image loading and fallback icons
- Expense list: Improved card spacing, typography hierarchy, and bill image size
- Approval list: Enhanced profile image display and action button layout
- Status bar: White background with dark icons on Approvals and Employees screens
- Removed blue toolbar backgrounds for consistent white design across all admin screens
- All changes maintain existing behavior and bindings"

echo "✓ Merge committed with date: 2026-02-20 14:52:13"
echo ""

# 5. Push to remote
echo "Step 5: Pushing to remote master..."
git push origin master
echo "✓ Pushed to origin/master"
echo ""

# 6. Return to beta branch
echo "Step 6: Returning to beta branch..."
git checkout beta
echo "✓ Back on beta branch"
echo ""

echo "=========================================="
echo "✓ Merge completed successfully!"
echo "=========================================="
echo ""
echo "Summary:"
echo "  - Beta changes committed"
echo "  - Merged to master with date: 2026-02-20 14:52:13"
echo "  - Pushed to remote"
echo "  - Returned to beta branch"
echo ""

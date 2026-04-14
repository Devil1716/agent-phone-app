# Contribution Graph Visibility

To ensure your commits appear on your GitHub contribution graph, several conditions must be met. This document outlines the most common reasons for missing contributions and how to resolve them.

## Common Reasons for Missing Commits

### 1. Commits are on a Non-Default Branch
GitHub only counts contributions for commits made to the repository's **default branch** (usually `main` or `master`) or the `gh-pages` branch. 
- **Action**: Merge your feature branches into `main` via a Pull Request.

### 2. Email Address Mismatch
The email address used in your local git configuration must be added and verified on your GitHub account.
- **Check local email**: Run `git config user.email` in your terminal.
- **Check GitHub**: Go to **Settings > Emails** on GitHub and verify the email matches.
- **Verification**: If the email is not verified, GitHub will not link the commits to your profile.

### 3. Branch Protection Rules
If the `main` branch is protected (requiring reviews or status checks), you cannot push directly to it.
- **Action**: Open a Pull Request on GitHub, ensure all status checks pass, and merge it. Commits will appear on the graph once merged into `main`.

### 4. Committing to a Fork
Commits to a fork are only counted if:
- You are an owner or collaborator of the upstream repository.
- **OR** The commits are merged into the upstream's default branch.

## How to Check Your Current Setup

Run the following command to see your current git configuration:
```bash
git config --list --show-origin
```
Look for `user.email`. Ensure it matches your GitHub login email.

## Best Practices
- **Configure Globally**: Set your name and email globally to avoid issues in new repositories:
  ```bash
  git config --global user.name "Your Name"
  git config --global user.email "your.email@example.com"
  ```
- **Frequent Merges**: Merge stable work into `main` regularly to keep your contribution history updated.

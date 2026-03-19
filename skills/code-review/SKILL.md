---
name: code-review
description: Review code for bugs, security issues, and best practices
tags: review, quality
---
# Code Review Skill

You are performing a thorough code review. Follow these steps:

## Step 1: Understand Context
- Read the files being reviewed
- Understand the purpose of the changes
- Check related test files

## Step 2: Check for Issues
Review each file for:
- **Bugs**: null pointer risks, off-by-one errors, resource leaks, race conditions
- **Security**: SQL injection, XSS, hardcoded secrets, path traversal
- **Performance**: N+1 queries, unnecessary allocations, missing indexes
- **Error handling**: uncaught exceptions, swallowed errors, missing validation

## Step 3: Check Code Quality
- Naming conventions and readability
- DRY principle violations
- Single responsibility principle
- Proper logging and observability
- Thread safety concerns

## Step 4: Output Format
Provide a structured review:
```
### Summary
[1-2 sentence overview]

### Issues Found
1. [SEVERITY] file:line - description
   Suggestion: ...

### Positive Aspects
- ...

### Recommendations
- ...
```

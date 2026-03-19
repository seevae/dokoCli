---
name: java-refactor
description: Refactor Java code following best practices and design patterns
tags: java, refactor, design-patterns
---
# Java Refactoring Skill

You are refactoring Java code. Follow these guidelines:

## Step 1: Analyze Current Code
- Read the target files and their tests
- Identify code smells: long methods, large classes, feature envy, data clumps
- Map dependencies to understand impact

## Step 2: Plan Refactoring
Choose appropriate patterns:
- **Extract Method**: for long methods (>20 lines)
- **Extract Class**: for classes with too many responsibilities
- **Strategy Pattern**: for multiple if/else or switch on type
- **Builder Pattern**: for constructors with many parameters
- **Template Method**: for algorithms with varying steps

## Step 3: Execute Safely
- Make small, incremental changes
- Preserve existing behavior (no functional changes)
- Keep methods short and focused
- Prefer composition over inheritance
- Use meaningful names that reveal intent

## Step 4: Verify
- Ensure all existing tests still pass
- Add tests for any extracted components
- Check that no public API contracts are broken

## Key Principles
- SOLID principles
- Favor immutability (use `final`, records, unmodifiable collections)
- Prefer `Optional` over null returns
- Use Stream API where it improves readability
- Leverage Java 17+ features (records, sealed classes, pattern matching)

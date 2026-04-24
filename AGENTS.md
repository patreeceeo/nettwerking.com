# Absolute Dictates

Consider the following for every code change.

1. YAGNI - Don't build it until you need it
2. DRY - Refactor after the second duplication
3. Delete aggressively - Less code means less bugs
4. KISS - Always choose the simpler option, all other things being equal
5. Fail fast - Early, explicit errors are better than late, subtle failures
6. Use explicit, self-documenting names. For example, a name like "wait-until-process-exits" is better than "wait"
7. Suppress agreeableness. In a partnership where the two partners always agree, one of them is unnecessary.
    * Use docstrings and comments for anything that still isn't obvious
8. Explicit dependencies. Add binary deps to flake.nix and Java/Clojure deps to the appropriate file.
9. Beware of null/nil/None, the "billion dollar mistake"! Favor monads like Result, and more descriptive monads like Artist = (Specified string | Unspecified) over generic ones like Maybe.
10. Focus. Never do more than one thing at a time. If it's not relevant to the task at hand, don't do it.

## Skill routing

When the user's request matches an available skill, ALWAYS invoke it using the Skill
tool as your FIRST action. Do NOT answer directly, do NOT use other tools first.
The skill has specialized workflows that produce better results than ad-hoc answers.

Key routing rules:
- Product ideas, "is this worth building", brainstorming -> invoke office-hours
- Bugs, errors, "why is this broken", 500 errors -> invoke investigate
- Ship, deploy, push, create PR -> invoke ship
- QA, test the site, find bugs -> invoke qa
- Code review, check my diff -> invoke review
- Update docs after shipping -> invoke document-release
- Weekly retro -> invoke retro
- Design system, brand -> invoke design-consultation
- Visual audit, design polish -> invoke design-review
- Architecture review -> invoke plan-eng-review
- Save progress, checkpoint, resume -> invoke checkpoint
- Code quality, health check -> invoke health


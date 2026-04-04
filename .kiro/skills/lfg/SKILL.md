---
name: lfg
description: Full autonomous engineering workflow
argument-hint: "[feature description]"
disable-model-invocation: true
---

CRITICAL: You MUST execute every step below IN ORDER. Do NOT skip any required step. Do NOT jump ahead to coding or implementation. The plan phase (step 2) MUST be completed and verified BEFORE any work begins. Violating this order produces bad output.

1. **Optional:** If the `ralph-loop` skill is available, run the ralph-loop-ralph-loop skill "finish all slash commands" --completion-promise "DONE"`. If not available or it fails, skip and continue to step 2 immediately.

2. the ce-plan skill $ARGUMENTS`

   GATE: STOP. Verify that the `ce:plan` workflow produced a plan file in `docs/plans/`. If no plan file was created, run the ce-plan skill $ARGUMENTS` again. Do NOT proceed to step 3 until a written plan exists. **Record the plan file path** — it will be passed to ce:review in step 4.

3. the ce-work skill

   GATE: STOP. Verify that implementation work was performed - files were created or modified beyond the plan. Do NOT proceed to step 4 if no code changes were made.

4. the ce-review skill mode:autofix plan:<plan-path-from-step-2>`

   Pass the plan file path from step 2 so ce:review can verify requirements completeness.

5. the compound-engineering-todo-resolve skill

6. the compound-engineering-test-browser skill

7. the compound-engineering-feature-video skill

8. Output `<promise>DONE</promise>` when video is in PR

Start with step 2 now (or step 1 if ralph-loop is available). Remember: plan FIRST, then work. Never skip the plan.

# CareRound - Auth admin (Postman)

Import `CareRound-AuthAdmin.postman_collection.json` and `CareRound-AuthAdmin.postman_environment.json`, pick the
**CareRound - Auth admin** environment and set `superAdminEmail` / `superAdminPassword`.

1. **00 - Bootstrap** logs in the super-admin and creates two wards (origin and target).
2. **01** creates a nurse in the origin ward and an admin without `ASSIGN_WARD`.
3. **MANUAL STEP**: set `nursePassword` and `limitedAdminPassword`, then open each account's activation link from the
   notification-service console and submit that password.
4. **03 - Reassign ward**: reassign → 200 (old/new ward + token note), the nurse logs in again and her token carries
   the target ward; unknown ward 404, ADMIN target 400, unknown user 404, missing `wardId` 400, admin without
   `ASSIGN_WARD` 403, the nurse herself 403.
5. **04 - Ward problems**: list (200, `{total, cap, items}`), admin without `ASSIGN_WARD` 403.

To run through the gateway, set `authBaseUrl` and `wardBaseUrl` to `http://localhost:8080`.

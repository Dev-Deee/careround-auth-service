package com.dayo.careround.authservice.controllers;

import com.dayo.careround.authservice.exception.InvalidVerificationTokenException;
import com.dayo.careround.authservice.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

// Serves the emailed activation link (a GET) until a frontend page takes over via app.activation.base-url.
// Activation also sets the user's first password, so the link opens a minimal set-password form that
// posts back here as a form; the JSON POST in AuthController is unchanged for API clients.
@RestController
@RequestMapping("/api/v1/auth/activate-account")
@RequiredArgsConstructor
public class ActivationPageController {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String INVALID_LINK =
            "This activation link is invalid, already used or expired. Ask your administrator to resend it.";

    private final AuthService authService;

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> showSetPasswordForm(@RequestParam String token) {
        try {
            authService.checkActivationToken(token);
        } catch (InvalidVerificationTokenException e) {
            return page(HttpStatus.BAD_REQUEST, message(INVALID_LINK));
        }
        return page(HttpStatus.OK, form(token, null));
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> activateFromForm(@RequestParam String token,
                                                   @RequestParam String password,
                                                   @RequestParam String confirmPassword) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return page(HttpStatus.BAD_REQUEST, form(token, "Password must be at least " + MIN_PASSWORD_LENGTH + " characters."));
        }
        if (!password.equals(confirmPassword)) {
            return page(HttpStatus.BAD_REQUEST, form(token, "Passwords do not match."));
        }
        try {
            authService.activateAccount(token, password);
        } catch (InvalidVerificationTokenException e) {
            return page(HttpStatus.BAD_REQUEST, message(INVALID_LINK));
        }
        return page(HttpStatus.OK, message("Your account is active. You can now log in to CareRound."));
    }

    private String form(String token, String error) {
        String errorHtml = error == null ? "" : "<p class=\"error\">" + HtmlUtils.htmlEscape(error) + "</p>";
        return """
                <h1>Activate your CareRound account</h1>
                <p>Choose a password to finish activating your account.</p>
                %s
                <form method="post" action="/api/v1/auth/activate-account">
                  <input type="hidden" name="token" value="%s">
                  <label>Password <input type="password" name="password" minlength="%d" required autocomplete="new-password"></label>
                  <label>Confirm password <input type="password" name="confirmPassword" minlength="%d" required autocomplete="new-password"></label>
                  <button type="submit">Activate account</button>
                </form>
                """.formatted(errorHtml, HtmlUtils.htmlEscape(token), MIN_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH);
    }

    private String message(String text) {
        return "<h1>CareRound account activation</h1><p>" + HtmlUtils.htmlEscape(text) + "</p>";
    }

    private ResponseEntity<String> page(HttpStatus status, String body) {
        String html = """
                <!doctype html>
                <html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>CareRound account activation</title>
                <style>
                  body { font-family: system-ui, sans-serif; max-width: 26rem; margin: 3rem auto; padding: 0 1rem; color: #1f2933; }
                  label { display: block; margin: 1rem 0; }
                  input[type=password] { display: block; width: 100%; padding: .5rem; margin-top: .25rem; box-sizing: border-box; }
                  button { padding: .6rem 1rem; }
                  .error { color: #b42318; }
                </style></head>
                <body>
                """ + body + "\n</body></html>\n";
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body(html);
    }
}

package com.khmermart.service;

import com.khmermart.common.response.TokenResponse;
import com.khmermart.model.request.AuthRequest;
import com.khmermart.model.request.ForgotPasswordRequest;
import com.khmermart.model.request.RegisterRequest;
import com.khmermart.model.request.ResendVerificationRequest;
import com.khmermart.model.request.ResetPasswordRequest;
import com.khmermart.model.request.VerifyEmailRequest;
import org.springframework.http.ResponseCookie;

public interface AuthService {

  record AuthResult(TokenResponse tokenResponse, ResponseCookie cookie) {}

  record RegisterResult(
      String email, boolean verificationRequired, int expiresInSeconds, int resendInSeconds) {}

  record ResendResult(String message, int expiresInSeconds, int resendInSeconds) {}

  RegisterResult register(RegisterRequest request);

  AuthResult login(AuthRequest request, String clientIp);

  AuthResult refresh(String rawToken);

  ResponseCookie logout(String rawToken);

  void forgotPassword(ForgotPasswordRequest request);

  void resetPassword(ResetPasswordRequest request);

  AuthResult verifyEmail(VerifyEmailRequest request);

  ResendResult resendVerification(ResendVerificationRequest request);
}

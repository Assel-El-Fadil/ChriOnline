package Server.handlers;

import Server.SessionManager;
import Server.service.UserService;
import Shared.DTO.UserDTO;
import Shared.ResponseBuilder;
import Shared.SessionData;

public class UserHandler {

    private final UserService userService;
    private final SessionManager sessionManager;

    public UserHandler(UserService userService, SessionManager sessionManager) {
        this.userService    = userService;
        this.sessionManager = sessionManager;
    }

    // ────────────────────────────────────────────────────────────
    //  GET_PROFILE
    //  params: [token]
    //  returns: OK|<UserDTO protocol string>  or  ERR|message
    // ────────────────────────────────────────────────────────────

    public String handleGetProfile(String[] params) {
        if (params.length < 1) {
            return ResponseBuilder.error("Missing parameters");
        }

        String token = params[0];
        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        try {
            UserDTO user = userService.getProfile(session.getUserId());
            return ResponseBuilder.ok(user.toProtocolString());

        } catch (UserService.UserNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (Exception e) {
            System.err.println("[UserHandler] GET_PROFILE error: " + e.getMessage());
            return ResponseBuilder.error("Could not load profile");
        }
    }

    // ────────────────────────────────────────────────────────────
    //  EDIT_PROFILE
    //  params: [token, field, value]
    //  returns: OK  or  ERR|message
    // ────────────────────────────────────────────────────────────

    public String handleEditProfile(String[] params) {
        if (params.length < 3) {
            return ResponseBuilder.error("Missing parameters");
        }

        String token = params[0];
        String field = params[1].trim();
        String value = params[2].trim();

        SessionData session = sessionManager.getSession(token);
        if (session == null) {
            return ResponseBuilder.error("Not logged in");
        }

        try {
            userService.updateProfile(session.getUserId(), field, value);
            System.out.println("[UserHandler] EDIT_PROFILE success — userId: "
                    + session.getUserId() + " field: " + field);
            return ResponseBuilder.ok();

        } catch (UserService.ValidationException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (UserService.DuplicateEmailException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (UserService.UserNotFoundException e) {
            return ResponseBuilder.error(e.getMessage());
        } catch (Exception e) {
            System.err.println("[UserHandler] EDIT_PROFILE error: " + e.getMessage());
            return ResponseBuilder.error("Could not update profile");
        }
    }
}

package Shared.DTO;

public class UserDTO {

    public int    id;
    public String username;
    public String email;
    public String role;
    public int active;

    public UserDTO() {}

    public UserDTO(int id, String username, String email, String role, int active) {
        this.id       = id;
        this.username = username;
        this.email    = email;
        this.role     = role;
        this.active   = active;
    }

    public String toProtocolString() {
        return "id="       + id
                + ",username="+ username
                + ",email="   + email
                + ",role="    + role
                + ",active="  + active;
    }

    public static UserDTO fromProtocolString(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Cannot parse blank UserDTO string");
        }

        UserDTO dto = new UserDTO();
        String[] pairs = s.split(",");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq == -1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();

            switch (key) {
                case "id":       dto.id       = Integer.parseInt(val); break;
                case "username": dto.username = val;                   break;
                case "email":    dto.email    = val;                   break;
                case "role":     dto.role     = val;                   break;
                case "active":   dto.active   = Integer.parseInt(val); break;
            }
        }
        return dto;
    }

    @Override
    public String toString() {
        return "UserDTO{id=" + id + ", username='" + username
                + "', role='" + role + "', active=" + active + "}";
    }
}

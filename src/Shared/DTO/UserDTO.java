package Shared.DTO;

public class UserDTO {

    public int id;
    public String username;
    public String firstName;
    public String lastName;
    public String email;
    public String address;
    public String profilePhoto;
    public String role;
    public int active;

    public UserDTO() {}

    public UserDTO(int id, String username, String firstName, String lastName,
                   String email, String address, String profilePhoto,
                   String role, int active) {
        this.id           = id;
        this.username     = username;
        this.firstName    = firstName;
        this.lastName     = lastName;
        this.email        = email;
        this.address      = address;
        this.profilePhoto = profilePhoto;
        this.role         = role;
        this.active       = active;
    }

    public UserDTO(int id, String username, String firstName, String lastName,
                   String email, String role, int active) {
        this(id, username, firstName, lastName, email, null, null, role, active);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public String toProtocolString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id=")        .append(id)
                .append(",username=") .append(sanitize(username))
                .append(",firstName=").append(sanitize(firstName))
                .append(",lastName=") .append(sanitize(lastName))
                .append(",email=")    .append(sanitize(email))
                .append(",role=")     .append(role)
                .append(",active=")   .append(active);

        if (address != null && !address.isBlank()) {
            sb.append(",address=").append(sanitize(address));
        }
        if (profilePhoto != null && !profilePhoto.isBlank()) {
            sb.append(",profilePhoto=").append(sanitize(profilePhoto));
        }

        return sb.toString();
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
                case "id":           dto.id           = Integer.parseInt(val); break;
                case "username":     dto.username     = val;                   break;
                case "firstName":    dto.firstName    = val;                   break;
                case "lastName":     dto.lastName     = val;                   break;
                case "email":        dto.email        = val;                   break;
                case "address":      dto.address      = val;                   break;
                case "profilePhoto": dto.profilePhoto = val;                   break;
                case "role":         dto.role         = val;                   break;
                case "active":       dto.active       = Integer.parseInt(val); break;
            }
        }
        return dto;
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

    @Override
    public String toString() {
        return "UserDTO{id=" + id
                + ", username='" + username + "'"
                + ", fullName='" + getFullName() + "'"
                + ", role='" + role + "'"
                + ", active=" + active + "}";
    }
}
package model;// User.java
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class User   implements Serializable {
    private final String username;
    private final String password;
    private final String department;
    private final String role;
    private List<String> permissions;

    public User(String username, String password, String department, String role) {
        this.username = username;
        this.password = password;
        this.department = department;
        this.role = role;
        this.permissions = new ArrayList<>();

        if (role.equals("manager")) {
            permissions.add("read");
            permissions.add("write");
            permissions.add("delete");
            permissions.add("manage_users");
        } else if (role.equals("employee") || role.equals("user")) {
            permissions.add("read");
        }
    }


    public boolean validatePassword(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    public String getUsername() {
        return username;
    }

    public String getDepartment() {
        return department;
    }

    public String getRole() {
        return role;
    }
    public List<String> getPermissions() {
        return new ArrayList<>(permissions);
    }
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public void setPermissions(List<String> newPermissions) {
        this.permissions = new ArrayList<>(newPermissions);
    }}
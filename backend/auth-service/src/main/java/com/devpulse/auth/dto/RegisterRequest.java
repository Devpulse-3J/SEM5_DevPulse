package com.devpulse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /auth/register}.
 */
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    private String fullName;

    private Integer companyId;

    private String companyName;

    private Boolean isCompany;

    // -- constructors --------------------------------------------------------

    public RegisterRequest() {
    }

    public RegisterRequest(String email, String password, String fullName, Integer companyId) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.companyId = companyId;
    }

    public RegisterRequest(String email, String password, String fullName, String companyName) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.companyName = companyName;
        this.isCompany = true;
    }

    // -- getters / setters ---------------------------------------------------

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public Integer getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Integer companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Boolean getIsCompany() {
        return isCompany;
    }

    public void setIsCompany(Boolean isCompany) {
        this.isCompany = isCompany;
    }
}

package com.example.springmvccontroller.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String paswrd;
    
    @Column(nullable = false)
    private String emal;
    
    @Column(nullable = false)
    private boolean enabled = true;
    
    @Column(nullable = false)
    private String role = "USER";


    //@Transient
    //private Address address;

    public User() {}
    
    public User(String username, String password, String email) {
        this.username = username;
        this.paswrd = password;
        this.emal = email;
        //this.address = new Address("123 Main St", "Anytown");
    }
    
    public User(String username, String password, String email, String role) {
        this.username = username;
        this.paswrd = password;
        this.emal = email;
        this.role = role;
        //this.address = new Address("123 Main St", "Anytown");
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPaswrd() {
        return paswrd;
    }
    
    public void setPaswrd(String password) {
        this.paswrd = password;
    }
    
    public String getEmal() {
        return emal;
    }
    
    public void setEmal(String email) {
        this.emal = email;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }

//     public Address getAddress() {
//         return address;
//     }
    
    
//     public void setAddress(Address address) {
//         this.address = address;
//     }
// }
}
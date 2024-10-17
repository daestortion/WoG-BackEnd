package com.respo.respo.Controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.respo.respo.Entity.CarEntity;
import com.respo.respo.Entity.OrderEntity;
import com.respo.respo.Entity.UserEntity;
import com.respo.respo.Entity.WalletEntity;
import com.respo.respo.Repository.OrderRepository;
import com.respo.respo.Repository.UserRepository;
import com.respo.respo.Service.UserService;
import com.respo.respo.Service.WalletService;

import java.util.Optional;
import com.respo.respo.Configuration.TokenGenerator; // Import TokenGenerator class

@RestController
@RequestMapping("/user")
@CrossOrigin(origins = ("https://main--wheelsongo.netlify.app"))
public class UserController {

    @Autowired
    UserService userv;

    @Autowired
    UserRepository urepo;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderRepository orepo;

    @Autowired
    private WalletService walletService;

    @GetMapping("/print")
    public String itWorks() {
        return "It works";
    }

    // Create
    @PostMapping("/insertUser")
    public UserEntity insertUser(@RequestBody UserEntity user) {
        return userv.insertUser(user);
    }

    // Read
    @GetMapping("/getAllUsers")
    public List<UserEntity> getAllUsers() {
        List<UserEntity> users = userv.getAllUsers();
        users.forEach(user -> System.out.println("User: " + user.getUserId() + ", isDeleted: " + user.isDeleted()));
        return users;
    }

    
    // U - Update a user record
    @PutMapping("/updateUser")
    public UserEntity updateUser(@RequestParam int userId, 
                                 @RequestParam(required = false) String pNum,
                                 @RequestParam(required = false) String email,
                                 @RequestPart(value = "profilePic", required = false) MultipartFile profilePic) {
        UserEntity user = userService.getUserById(userId);
        if (email != null && !email.isEmpty()) user.setEmail(email);
        if (pNum != null && !pNum.isEmpty()) user.setpNum(pNum);
        if (profilePic != null && !profilePic.isEmpty()) {
            try {
                byte[] bytes = profilePic.getBytes();
                user.setProfilePic(bytes);
            } catch (IOException e) {
                e.printStackTrace();  // handle the exception
            }
        }
        return userService.updateUser(userId, user);  // Ensure that this method accepts both the userId and UserEntity.
    }

    // D - Delete a user record
    @PutMapping("/deleteUser/{userId}")
    public ResponseEntity<String> deleteUser(@PathVariable int userId) {
        try {
            String result = userv.deleteUser(userId);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (NoSuchElementException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    // Reactivate
    @PutMapping("/reactivateUser/{userId}")
    public String reactivateUser(@PathVariable int userId) {
        return userv.reactivateUser(userId);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> credentials) {
        String identifier = credentials.get("identifier");
        String password = credentials.get("password");

        try {
            Optional<UserEntity> user = userService.validateUser(identifier, password);
            if (user.isPresent()) {
                return ResponseEntity.ok(user.get());
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
            }
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Account is deleted.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
        }
    }
    
    @GetMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String identifier) {
        try {
            UserEntity user = userv.getUserByIdentifier(identifier);
            if (user != null) {
                // Generate a reset token and store it with an expiry time in your database
                String resetToken = TokenGenerator.generateResetToken(user.getUserId()); // Using TokenGenerator to generate reset token
                String resetLink = "http://localhost:3000/resetpassword?userId=" + user.getUserId() + "&token=" + resetToken;
                userv.sendPasswordResetEmail(user, resetLink);
                return ResponseEntity.ok("If the email is associated with an account, a reset link has been sent.");
            } else {
                return ResponseEntity.badRequest().body("No account associated with this email.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred while processing your request: " + e.getMessage());
        }
    }
    
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> requestBody) {
        try {
            int userId = Integer.parseInt(requestBody.get("userId"));
            String newPassword = requestBody.get("newPassword");
            
            UserEntity user = userv.resetPassword(userId, newPassword);
            return ResponseEntity.ok("Password for user " + user.getUsername() + " has been successfully updated.");
        } catch (NoSuchElementException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (NumberFormatException | NullPointerException e) {
            return ResponseEntity.badRequest().body("Invalid userId provided");
        }
    }


    @PutMapping("/updateIsOwner/{userId}")
    public ResponseEntity<?> updateIsOwner(@PathVariable int userId, @RequestBody Map<String, Boolean> updates) {
        try {
            // Get the user by ID
            UserEntity user = userService.getUserById(userId);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
            }

            // Check if "isOwner" key exists in the request body
            if (updates.containsKey("isOwner")) {
                boolean isOwner = updates.get("isOwner");

                // Update the isOwner flag
                user.setOwner(isOwner);
                userService.updateUser(user);

                // If the user is now an owner and doesn't already have a wallet, create one
                if (isOwner && user.getWallet() == null) {
                    WalletEntity wallet = new WalletEntity(); // Create a new wallet
                    wallet.setUser(user);  // Associate the wallet with the user
                    wallet.setBalance(0.0);  // Initialize the wallet with 0 balance
                    wallet.setActive(true);  // Mark the wallet as active

                    walletService.createWallet(wallet); // Save the wallet

                    // Update the user's wallet reference and save the user again
                    user.setWallet(wallet);
                    userService.updateUser(user);
                }

                return ResponseEntity.ok(user);
            }

            return ResponseEntity.badRequest().body("Invalid request");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/getUserById/{userId}")
    public ResponseEntity<UserEntity> getUserById(@PathVariable int userId) {
        try {
            UserEntity user = userService.getUserById(userId);
            if (user != null) {
                return new ResponseEntity<>(user, HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/checkDatabaseEmpty") // Corrected the mapping
    public ResponseEntity<Boolean> checkDatabaseEmpty() {
        boolean isEmpty = urepo.count() == 0;
        return ResponseEntity.ok(isEmpty);
    }
    
    @GetMapping("/getAllOrdersFromUser/{userId}")
    public ResponseEntity<List<OrderEntity>> getAllOrdersFromUser(@PathVariable int userId) {
        try {
            List<OrderEntity> orders = userv.getAllOrdersByUserId(userId);
            if (orders.isEmpty()) {
                return ResponseEntity.noContent().build(); // No orders found
            }
            return ResponseEntity.ok(orders); // Return the list of orders
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(null); // Internal server error
        }
    }
    
    @GetMapping("/getOwnedCarsByUserId/{userId}")
    public ResponseEntity<List<CarEntity>> getOwnedCarsByUserId(@PathVariable int userId) {
        try {
            UserEntity user = userService.getUserById(userId);
            if (user != null) {
                List<CarEntity> ownedCars = user.getCars();
                if (ownedCars.isEmpty()) {
                    return new ResponseEntity<>(HttpStatus.NO_CONTENT); // No cars found
                }
                return new ResponseEntity<>(ownedCars, HttpStatus.OK); // Return the list of owned cars
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND); // User not found
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // Internal server error
        }
    }
    

    @GetMapping("/{userId}/carOrders")
    public ResponseEntity<List<OrderEntity>> getCarOrdersByUserId(@PathVariable int userId) {
        try {
            UserEntity user = userService.getUserById(userId);
            if (user != null) {
                List<CarEntity> ownedCars = user.getCars();
                List<OrderEntity> allOrders = new ArrayList<>();
                for (CarEntity car : ownedCars) {
                    allOrders.addAll(car.getOrders());
                }
                if (allOrders.isEmpty()) {
                    return new ResponseEntity<>(HttpStatus.NO_CONTENT); // No orders found
                }
                // Sort orders by orderId in descending order to show newest orders at the top
                Collections.sort(allOrders, new Comparator<OrderEntity>() {
                    @Override
                    public int compare(OrderEntity o1, OrderEntity o2) {
                        return Integer.compare(o2.getOrderId(), o1.getOrderId());
                    }
                });
                return new ResponseEntity<>(allOrders, HttpStatus.OK); // Return all orders from all cars owned by the user
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND); // User not found
            }
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // Internal server error
        }
    }

    @PostMapping("/logUserAction")
    public ResponseEntity<String> logUserAction(@RequestBody Map<String, Object> logData) {
        System.out.println("Log User Action:");
        System.out.println("Action: " + logData.get("action"));
        System.out.println("Timestamp: " + logData.get("timestamp"));
        System.out.println("User Data: " + logData.get("userData"));
        
        // You can extend this to save the logs into a file, database, etc.
        
        return new ResponseEntity<>("User action logged successfully", HttpStatus.OK);
    }
}


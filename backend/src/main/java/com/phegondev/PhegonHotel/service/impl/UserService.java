
package com.phegondev.PhegonHotel.service.impl;

import com.phegondev.PhegonHotel.dto.LoginRequest;
import com.phegondev.PhegonHotel.dto.Response;
import com.phegondev.PhegonHotel.dto.UserDTO;
import com.phegondev.PhegonHotel.dto.BookingDTO;
import com.phegondev.PhegonHotel.dto.RoomDTO;
import com.phegondev.PhegonHotel.entity.User;
import com.phegondev.PhegonHotel.exception.OurException;
import com.phegondev.PhegonHotel.repo.UserRepository;
import com.phegondev.PhegonHotel.service.AwsS3Service;
import com.phegondev.PhegonHotel.service.interfac.IUserService;
import com.phegondev.PhegonHotel.utils.JWTUtils;
import com.phegondev.PhegonHotel.utils.Utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Service
public class UserService implements IUserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JWTUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AwsS3Service awsS3Service;

    /**
     * Generates a fresh pre-signed URL for an existing S3 image.
     */
    private String getFreshImageUrl(String storedUrl) {
        if (storedUrl == null || storedUrl.isBlank()) {
            return storedUrl;
        }

        try {
            String filename;

            if (storedUrl.startsWith("http://")
                    || storedUrl.startsWith("https://")) {

                URI uri = URI.create(storedUrl);
                String path = uri.getPath();

                if (path == null || path.isBlank()) {
                    throw new OurException("Invalid S3 image URL.");
                }

                filename = path.substring(path.lastIndexOf("/") + 1);

            } else {
                filename = storedUrl.substring(
                        storedUrl.lastIndexOf("/") + 1
                );
            }

            if (filename.isBlank()) {
                throw new OurException("S3 image filename is missing.");
            }

            return awsS3Service.generatePresignedImageUrl(filename);

        } catch (OurException e) {
            throw e;
        } catch (Exception e) {
            throw new OurException(
                    "Unable to generate room image URL: " + e.getMessage()
            );
        }
    }

    @Override
    public Response register(User user) {
        Response response = new Response();

        try {
            if (user.getRole() == null || user.getRole().isBlank()) {
                user.setRole("USER");
            }

            if (userRepository.existsByEmail(user.getEmail())) {
                throw new OurException(
                        user.getEmail() + "Already Exists"
                );
            }

            user.setPassword(passwordEncoder.encode(user.getPassword()));

            User savedUser = userRepository.save(user);
            UserDTO userDTO = Utils.mapUserEntityToUserDTO(savedUser);

            response.setStatusCode(200);
            response.setUser(userDTO);

        } catch (OurException e) {
            response.setStatusCode(400);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error Occurred During User Registration: "
                            + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response login(LoginRequest loginRequest) {
        Response response = new Response();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(),
                            loginRequest.getPassword()
                    )
            );

            var user = userRepository.findByEmail(loginRequest.getEmail())
                    .orElseThrow(
                            () -> new OurException("User Not Found")
                    );

            var token = jwtUtils.generateToken(user);

            response.setStatusCode(200);
            response.setToken(token);
            response.setRole(user.getRole());
            response.setExpirationTime("7 Days");
            response.setMessage("successful");

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error Occurred During User Login: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getAllUsers() {
        Response response = new Response();

        try {
            List<User> userList = userRepository.findAll();

            List<UserDTO> userDTOList =
                    Utils.mapUserListEntityToUserListDTO(userList);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setUserList(userDTOList);

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error getting all users: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getUserBookingHistory(String userId) {
        Response response = new Response();

        try {
            User user = userRepository.findById(Long.valueOf(userId))
                    .orElseThrow(
                            () -> new OurException("User Not Found")
                    );

            UserDTO userDTO =
                    Utils.mapUserEntityToUserDTOPlusUserBookingsAndRoom(user);

            // Refresh the image URL for every room in booking history.
            if (userDTO.getBookings() != null) {
                for (BookingDTO booking : userDTO.getBookings()) {
                    if (booking.getRoom() != null) {
                        RoomDTO roomDTO = booking.getRoom();

                        roomDTO.setRoomPhotoUrl(
                                getFreshImageUrl(
                                        roomDTO.getRoomPhotoUrl()
                                )
                        );
                    }
                }
            }

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setUser(userDTO);

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error getting user booking history: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response deleteUser(String userId) {
        Response response = new Response();

        try {
            userRepository.findById(Long.valueOf(userId))
                    .orElseThrow(
                            () -> new OurException("User Not Found")
                    );

            userRepository.deleteById(Long.valueOf(userId));

            response.setStatusCode(200);
            response.setMessage("successful");

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error deleting user: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getUserById(String userId) {
        Response response = new Response();

        try {
            User user = userRepository.findById(Long.valueOf(userId))
                    .orElseThrow(
                            () -> new OurException("User Not Found")
                    );

            UserDTO userDTO = Utils.mapUserEntityToUserDTO(user);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setUser(userDTO);

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error getting user: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getMyInfo(String email) {
        Response response = new Response();

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(
                            () -> new OurException("User Not Found")
                    );

            UserDTO userDTO = Utils.mapUserEntityToUserDTO(user);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setUser(userDTO);

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error getting user information: " + e.getMessage()
            );
        }

        return response;
    }
}

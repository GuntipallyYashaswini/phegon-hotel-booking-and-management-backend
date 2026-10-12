
package com.phegondev.PhegonHotel.service.impl;

import com.phegondev.PhegonHotel.dto.Response;
import com.phegondev.PhegonHotel.dto.RoomDTO;
import com.phegondev.PhegonHotel.entity.Room;
import com.phegondev.PhegonHotel.exception.OurException;
import com.phegondev.PhegonHotel.repo.BookingRepository;
import com.phegondev.PhegonHotel.repo.RoomRepository;
import com.phegondev.PhegonHotel.service.AwsS3Service;
import com.phegondev.PhegonHotel.service.interfac.IRoomService;
import com.phegondev.PhegonHotel.utils.Utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@Service
public class RoomService implements IRoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private AwsS3Service awsS3Service;

    /**
     * Generates a fresh pre-signed URL for an existing S3 image.
     * Supports stored direct S3 URLs and pre-signed S3 URLs.
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
                // Supports a database value containing only the S3 key.
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

    /**
     * Refreshes the image URL in a room DTO.
     */
    private RoomDTO refreshRoomImage(RoomDTO roomDTO) {
        if (roomDTO != null) {
            roomDTO.setRoomPhotoUrl(
                    getFreshImageUrl(roomDTO.getRoomPhotoUrl())
            );
        }

        return roomDTO;
    }

    /**
     * Refreshes image URLs for a list of room DTOs.
     */
    private List<RoomDTO> refreshRoomImages(List<RoomDTO> roomDTOList) {
        for (RoomDTO roomDTO : roomDTOList) {
            refreshRoomImage(roomDTO);
        }

        return roomDTOList;
    }

    @Override
    public Response addNewRoom(
            MultipartFile photo,
            String roomType,
            BigDecimal roomPrice,
            String description) {

        Response response = new Response();

        try {
            String imageUrl = awsS3Service.saveImageToS3(photo);

            Room room = new Room();
            room.setRoomPhotoUrl(imageUrl);
            room.setRoomType(roomType);
            room.setRoomPrice(roomPrice);
            room.setRoomDescription(description);

            Room savedRoom = roomRepository.save(room);

            RoomDTO roomDTO =
                    Utils.mapRoomEntityToRoomDTO(savedRoom);

            refreshRoomImage(roomDTO);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setRoom(roomDTO);

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error saving a room: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public List<String> getAllRoomTypes() {
        return roomRepository.findDistinctRoomTypes();
    }

    @Override
    public Response getAllRooms() {
        Response response = new Response();

        try {
            // Fetch rooms from the database.
            List<Room> roomList = roomRepository.findAll(
                    Sort.by(Sort.Direction.DESC, "id")
            );

            // Convert entities to DTOs.
            List<RoomDTO> roomDTOList =
                    Utils.mapRoomListEntityToRoomListDTO(roomList);

            // Generate fresh pre-signed URLs for room images.
            refreshRoomImages(roomDTOList);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setRoomList(roomDTOList);

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error retrieving rooms: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response deleteRoom(Long roomId) {
        Response response = new Response();

        try {
            roomRepository.findById(roomId)
                    .orElseThrow(
                            () -> new OurException("Room Not Found")
                    );

            roomRepository.deleteById(roomId);

            response.setStatusCode(200);
            response.setMessage("successful");

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error deleting a room: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response updateRoom(
            Long roomId,
            String description,
            String roomType,
            BigDecimal roomPrice,
            MultipartFile photo) {

        Response response = new Response();

        try {
            String imageUrl = null;

            if (photo != null && !photo.isEmpty()) {
                imageUrl = awsS3Service.saveImageToS3(photo);
            }

            Room room = roomRepository.findById(roomId)
                    .orElseThrow(
                            () -> new OurException("Room Not Found")
                    );

            if (roomType != null) {
                room.setRoomType(roomType);
            }

            if (roomPrice != null) {
                room.setRoomPrice(roomPrice);
            }

            if (description != null) {
                room.setRoomDescription(description);
            }

            if (imageUrl != null) {
                room.setRoomPhotoUrl(imageUrl);
            }

            Room updatedRoom = roomRepository.save(room);

            RoomDTO roomDTO =
                    Utils.mapRoomEntityToRoomDTO(updatedRoom);

            refreshRoomImage(roomDTO);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setRoom(roomDTO);

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error updating a room: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getRoomById(Long roomId) {
        Response response = new Response();

        try {
            Room room = roomRepository.findById(roomId)
                    .orElseThrow(
                            () -> new OurException("Room Not Found")
                    );

            RoomDTO roomDTO =
                    Utils.mapRoomEntityToRoomDTOPlusBookings(room);

            refreshRoomImage(roomDTO);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setRoom(roomDTO);

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error retrieving room: " + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getAvailableRoomsByDataAndType(
            LocalDate checkInDate,
            LocalDate checkOutDate,
            String roomType) {

        Response response = new Response();

        try {
            List<Room> availableRooms =
                    roomRepository.findAvailableRoomsByDatesAndTypes(
                            checkInDate,
                            checkOutDate,
                            roomType
                    );

            List<RoomDTO> roomDTOList =
                    Utils.mapRoomListEntityToRoomListDTO(
                            availableRooms
                    );

            refreshRoomImages(roomDTOList);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setRoomList(roomDTOList);

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error retrieving available rooms: "
                            + e.getMessage()
            );
        }

        return response;
    }

    @Override
    public Response getAllAvailableRooms() {
        Response response = new Response();

        try {
            List<Room> roomList =
                    roomRepository.getAllAvailableRooms();

            List<RoomDTO> roomDTOList =
                    Utils.mapRoomListEntityToRoomListDTO(roomList);

            refreshRoomImages(roomDTOList);

            response.setStatusCode(200);
            response.setMessage("successful");
            response.setRoomList(roomDTOList);

        } catch (OurException e) {
            response.setStatusCode(404);
            response.setMessage(e.getMessage());

        } catch (Exception e) {
            response.setStatusCode(500);
            response.setMessage(
                    "Error retrieving available rooms: "
                            + e.getMessage()
            );
        }

        return response;
    }
}

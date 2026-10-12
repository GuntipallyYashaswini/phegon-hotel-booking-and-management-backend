
package com.phegondev.PhegonHotel.service;

import com.amazonaws.HttpMethod;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.phegondev.PhegonHotel.exception.OurException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Service
public class AwsS3Service {

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.access.key}")
    private String awsS3AccessKey;

    @Value("${aws.s3.secret.key}")
    private String awsS3SecretKey;

    @Value("${aws.s3.region}")
    private String awsS3Region;

    private AmazonS3 createS3Client() {
        BasicAWSCredentials credentials =
                new BasicAWSCredentials(awsS3AccessKey, awsS3SecretKey);

        return AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withRegion(awsS3Region)
                .build();
    }

    public String saveImageToS3(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new OurException("The uploaded image is empty.");
        }

        AmazonS3 s3Client = createS3Client();

        try {
            String originalFilename = photo.getOriginalFilename();
            String extension = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(
                        originalFilename.lastIndexOf(".")
                );
            }

            // Unique key prevents different room images from overwriting each other.
            String s3Filename = UUID.randomUUID() + extension;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(photo.getSize());

            if (photo.getContentType() != null) {
                metadata.setContentType(photo.getContentType());
            }

            try (InputStream inputStream = photo.getInputStream()) {
                PutObjectRequest uploadRequest = new PutObjectRequest(
                        bucketName,
                        s3Filename,
                        inputStream,
                        metadata
                );

                s3Client.putObject(uploadRequest);
            }

            // Return a temporary URL because the S3 bucket is private.
            return generatePresignedImageUrl(s3Filename);

        } catch (Exception e) {
            e.printStackTrace();
            throw new OurException(
                    "Unable to upload image to S3 bucket: " + e.getMessage()
            );
        }
    }

    /**
     * Generates a temporary URL for displaying an image from a private bucket.
     * The URL expires after one hour.
     */
    public String generatePresignedImageUrl(String s3Filename) {
        if (s3Filename == null || s3Filename.isBlank()) {
            throw new OurException("Image filename is missing.");
        }

        try {
            AmazonS3 s3Client = createS3Client();

            Date expiration = new Date(
                    System.currentTimeMillis() + 60L * 60 * 1000
            );

            GeneratePresignedUrlRequest request =
                    new GeneratePresignedUrlRequest(bucketName, s3Filename)
                            .withMethod(HttpMethod.GET)
                            .withExpiration(expiration);

            URL url = s3Client.generatePresignedUrl(request);
            return url.toExternalForm();

        } catch (Exception e) {
            e.printStackTrace();
            throw new OurException(
                    "Unable to generate image URL: " + e.getMessage()
            );
        }
    }
}

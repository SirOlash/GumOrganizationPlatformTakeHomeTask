package com.Semicolon.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrgProfileRequest {
    private String logoUrl;

    @URL(message = "website must be a valid URL")
    private String website;

    @Size(max = 255, message = "industry max length is 255 chars")
    private String industry;

    @Size(max = 2000, message = "description max length is 2000 chars")
    private String description;
}

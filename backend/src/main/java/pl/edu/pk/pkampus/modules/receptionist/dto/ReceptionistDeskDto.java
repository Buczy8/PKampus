package pl.edu.pk.pkampus.modules.receptionist.dto;

import java.util.List;

public record ReceptionistDeskDto(
        List<DeskLaundryBookingDto> laundry,
        List<DeskRoomBookingDto> rooms,
        int openIssuesCount,
        List<DeskOpenIssueDto> openIssues
) {
}

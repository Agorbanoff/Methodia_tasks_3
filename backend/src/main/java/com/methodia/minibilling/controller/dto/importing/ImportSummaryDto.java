package com.methodia.minibilling.controller.dto.importing;

import java.util.List;

public record ImportSummaryDto(
        int importedUsers,
        int importedReadings,
        int importedPrices,
        int skippedDuplicates,
        List<String> errors
) {
}

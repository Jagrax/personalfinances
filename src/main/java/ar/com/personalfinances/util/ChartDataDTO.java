package ar.com.personalfinances.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartDataDTO {

    private List<String> labels;
    private List<ChartDatasetDTO> datasets;
}
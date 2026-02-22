package ar.com.personalfinances.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChartDatasetDTO {

    private String label;
    private List<Number> data;
}
package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Menu {
    private List<MenuItem> items;

    public Menu(List<MenuItem> items) {
        this.items = items;
    }
}
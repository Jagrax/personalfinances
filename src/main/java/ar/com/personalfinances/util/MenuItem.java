package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MenuItem {

    private String id;
    private String icon;
    private String url;
    private String text;
    private List<MenuItem> subItems;

    public MenuItem(String id, String icon, String text, String url, List<MenuItem> subItems) {
        this.icon = icon;
        this.text = text;
        this.id = id;
        this.url = url;
        this.subItems = subItems;
    }
}
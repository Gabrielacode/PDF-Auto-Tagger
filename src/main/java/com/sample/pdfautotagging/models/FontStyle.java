package com.sample.pdfautotagging.models;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false,onlyExplicitlyIncluded = true)


public class FontStyle {
    @EqualsAndHashCode.Include()
    double fontSize;
    int appearance = 0;
    @EqualsAndHashCode.Include()
    String name;

}

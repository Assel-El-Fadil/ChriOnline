package Shared.DTO;

import Shared.Command;
import Shared.InvalidRequestException;

public enum Category {
    SANTE,
    VETEMENTS,
    ELECTRONIQUES,
    ELECTROMENAGER,
    JEUX_VIDEO,
    BEAUTE_ET_COSMETIQUES,
    FITNESS;

    public static Category fromString(String cat) throws InvalidRequestException {
        if (cat == null) {
            throw new InvalidRequestException("Invalid Category");
        }

        try {
            return Category.valueOf(cat.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid Category");
        }
    }
}

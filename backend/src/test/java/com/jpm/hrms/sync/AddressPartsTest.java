package com.jpm.hrms.sync;

import com.jpm.hrms.sync.AddressParts.Parts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressPartsTest {

    @Test
    void movesPinAndRepeatedTownOutOfTheStreetLine() {
        Parts p = AddressParts.tidy("3/2120 vel nagar thandangoundan pudhur, kalipalayam, tirupur 641666", "Tirupur", "Tiruppur", "Tamil Nadu", null);
        assertThat(p.address()).isEqualTo("3/2120 vel nagar thandangoundan pudhur, kalipalayam");
        assertThat(p.pincode()).isEqualTo("641666");
        assertThat(p.city()).isEqualTo("Tirupur");
    }

    @Test
    void fillsTownAndStateWhenOnlyTheStreetQuestionWasAnswered() {
        Parts p = AddressParts.tidy("12, Main Road, Gandhipuram, coimbatore, Tamil Nadu - 641 012, India", null, null, null, null);
        assertThat(p.address()).isEqualTo("12, Main Road, Gandhipuram");
        assertThat(p.city()).isEqualTo("Coimbatore");
        assertThat(p.state()).isEqualTo("Tamil Nadu");
        assertThat(p.pincode()).isEqualTo("641012");
    }

    @Test
    void dropsRepeatedDistrictWithDifferentSpelling() {
        Parts p = AddressParts.tidy("No 5, Anna Street, Avinashi, Tiruppur Dt 641654", "Avinashi", "Tirupur", "Tamil Nadu", "641654");
        assertThat(p.address()).isEqualTo("No 5, Anna Street");
    }

    @Test
    void leavesAProperStreetLineAlone() {
        Parts p = AddressParts.tidy("3/2120 Vel Nagar, Kalipalayam", "Tirupur", "Tiruppur", "Tamil Nadu", "641666");
        assertThat(p).isEqualTo(new Parts("3/2120 Vel Nagar, Kalipalayam", "Tirupur", "Tiruppur", "Tamil Nadu", "641666"));
    }

    @Test
    void doesNotTouchAddressesWithoutAPinAtTheEndOrWithADifferentPin() {
        assertThat(AddressParts.tidy("Plot 641666 Industrial Estate, Avinashi Road", null, null, null, null).address())
                .isEqualTo("Plot 641666 Industrial Estate, Avinashi Road");
        Parts p = AddressParts.tidy("5, Anna Street, Tirupur 641666", "Tirupur", null, null, "641604");
        assertThat(p.address()).isEqualTo("5, Anna Street, Tirupur 641666");
        assertThat(p.pincode()).isEqualTo("641604");
    }

    @Test
    void ignoresPhoneNumbersAndKeepsTheStreetLineWhenNothingElseIsLeft() {
        assertThat(AddressParts.tidy("Near bus stand, call 9876543210", null, null, null, null).address())
                .isEqualTo("Near bus stand, call 9876543210");
        assertThat(AddressParts.tidy("Tirupur 641666", "Tirupur", null, null, null).address()).isEqualTo("Tirupur 641666");
    }
}

package com.pillora.pillora.navigation

enum class Screen(val route: String) {
    Home("home"),
    Firestore("firestore"),
    Terms("terms"),
    MedicineForm("medicine_form"),
    MedicineList("medicine_list");
}

enum class FrequencyType {
    TIMES_PER_DAY,
    EVERY_X_HOURS
}

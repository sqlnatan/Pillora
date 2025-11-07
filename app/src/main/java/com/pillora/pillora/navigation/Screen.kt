package com.pillora.pillora.navigation

enum class Screen(val route: String) {
    Home("home"),
    // Firestore("firestore"), // Comentado pois não está sendo usado
    Terms("terms"),
    MedicineForm("medicine_form"),
    MedicineList("medicine_list"),
    Settings("settings"), // Adicionar rota para Configurações
    ConsultationList("consultation_list"),
    ConsultationForm("consultation_form"), // Rota base para o formulário
    VaccineList("vaccine_list"), // Nova rota para lista de vacinas
    VaccineForm("vaccine_form"), // Nova rota para formulário de vacinas
    Profile("profile"),
    Reports("reports");
}

/* // Comentado pois não está sendo usado
enum class FrequencyType {
    TIMES_PER_DAY,
    EVERY_X_HOURS
}
*/


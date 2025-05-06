package com.pillora.pillora.model

/**
 * Representa uma receita médica.
 *
 * @property id O ID único do documento no Firestore (gerado automaticamente).
 * @property userId O ID do usuário proprietário desta receita.
 * @property patientName O nome do paciente para quem a receita foi emitida.
 * @property doctorName O nome do médico que prescreveu a receita.
 * @property crm O número do CRM do médico (opcional).
 * @property prescriptionDate A data em que a receita foi emitida (formato DD/MM/YYYY).
 * @property prescribedMedications Uma lista dos medicamentos prescritos nesta receita.
 * @property generalInstructions Instruções gerais aplicáveis a toda a receita.
 * @property notes Notas adicionais ou observações sobre a receita.
 * @property imageUri URI (String) opcional para uma imagem da receita física (armazenada no Firebase Storage, por exemplo).
 */
data class Recipe(
    val id: String? = null,
    val userId: String? = null,
    val patientName: String = "",
    val doctorName: String = "",
    val crm: String = "", // Optional CRM
    val prescriptionDate: String = "", // Format DD/MM/YYYY
    val prescribedMedications: List<PrescribedMedication> = emptyList(),
    val generalInstructions: String = "", // General instructions for the whole recipe
    val notes: String = "",
    val imageUri: String? = null, // Optional URI for prescription image
    val validityDate: String = "" // Format DD/MM/YYYY - NEW FIELD
)

/**
 * Representa um medicamento específico prescrito dentro de uma receita.
 *
 * @property name O nome do medicamento.
 * @property dose A dosagem prescrita (ex: "500mg", "10ml").
 * @property instructions Instruções específicas para tomar este medicamento (ex: "1 comprimido a cada 8 horas por 7 dias").
 */
data class PrescribedMedication(
    val name: String = "",
    val dose: String = "",
    val instructions: String = "" // Specific instructions for this medication
)


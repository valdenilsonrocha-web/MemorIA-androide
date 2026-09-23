package com.memoria.mobile.reminders

/**
 * The rotating reminder texts of the web's `notificationService.js`, word for
 * word, so a reminder reads the same on the phone and in the browser.
 */
data class ReminderMessage(val title: String, val body: String)

object ReminderTexts {

    /** `getMedicationReminderVariants()`. */
    fun medicationVariants(scheduleTime: String): List<ReminderMessage> {
        val t = scheduleTime.ifBlank { "--:--" }
        return listOf(
            ReminderMessage(
                "Vamos lhe ajudar a lembrar.",
                "Chegou a hora do remédio das $t - não esqueça de marcar como tomada.",
            ),
            ReminderMessage(
                "Lembrete importante.",
                "Seu remédio das $t está no horário. Depois de tomar, marque como tomada no aplicativo.",
            ),
            ReminderMessage(
                "Hora do cuidado com você.",
                "Agora é o momento do remédio das $t. Tome e confirme como tomada para manter o controle em dia.",
            ),
            ReminderMessage(
                "Atenção ao seu tratamento.",
                "Está na hora do remédio das $t. Não esqueça de registrar como tomada.",
            ),
        )
    }

    /**
     * `getReplenishmentReminderVariants()`. The web strings are missing their
     * accents ("reposicao", "esta"…); here they are written correctly.
     */
    fun replenishmentVariants(medicationName: String, stockLevel: Int): List<ReminderMessage> {
        val name = medicationName.trim().ifBlank { "seu medicamento" }
        val stockText = "${stockLevel.coerceAtLeast(0)} dose(s)"
        return listOf(
            ReminderMessage(
                "Hora de planejar a reposição.",
                "$name está com estoque em $stockText. Organize a compra para manter o tratamento em dia.",
            ),
            ReminderMessage(
                "Atenção ao estoque do tratamento.",
                "$name está com apenas $stockText. Reponha em breve para evitar interrupções.",
            ),
            ReminderMessage(
                "Lembrete de reposição importante.",
                "$name chegou ao nível de $stockText. Programe a reposição o quanto antes.",
            ),
            ReminderMessage(
                "Seu cuidado continua.",
                "Para não faltar $name, reponha o estoque atual de $stockText e mantenha sua rotina segura.",
            ),
        )
    }

    /**
     * `formatConsultationDateTime()`: pt-BR `dateStyle/timeStyle: 'short'`
     * ("22/09/2026, 14:30"), or "horário não informado".
     */
    fun formatConsultationDateTime(dateTime: String): String = runCatching {
        java.time.LocalDateTime.parse(dateTime.trim())
            .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm"))
    }.getOrDefault("horário não informado")

    /** `getConsultationReminderVariants()`. */
    fun consultationVariants(professional: String, dateTime: String): List<ReminderMessage> {
        val who = professional.trim().ifBlank { "sua equipe médica" }
        val whenText = formatConsultationDateTime(dateTime)
        return listOf(
            ReminderMessage(
                "Consulta agendada chegando.",
                "Você tem consulta com $who em $whenText. Revise seus sintomas e leve seus registros.",
            ),
            ReminderMessage(
                "Seu acompanhamento importa.",
                "Está na hora da consulta com $who ($whenText). Abra o MemorIA e confira os detalhes.",
            ),
            ReminderMessage(
                "Lembrete de consulta médica.",
                "Consulta marcada com $who para $whenText. Organize-se para não perder esse cuidado.",
            ),
            ReminderMessage(
                "Hora de cuidar da saúde.",
                "Seu atendimento com $who está agendado para $whenText. Tenha tudo em mãos para aproveitar melhor a consulta.",
            ),
        )
    }

    private var lastConsultationIndex = -1

    /** `getRandomConsultationReminderMessage()`. */
    @Synchronized
    fun randomConsultation(professional: String, dateTime: String): ReminderMessage {
        val variants = consultationVariants(professional, dateTime)
        val index = pick(variants.size, lastConsultationIndex)
        lastConsultationIndex = index
        return variants[index]
    }

    private var lastMedicationIndex = -1
    private var lastReplenishmentIndex = -1

    /** `getRandomMedicationReminderMessage()`: random, never the same one twice in a row. */
    @Synchronized
    fun randomMedication(scheduleTime: String): ReminderMessage {
        val variants = medicationVariants(scheduleTime)
        val index = pick(variants.size, lastMedicationIndex)
        lastMedicationIndex = index
        return variants[index]
    }

    /** `getRandomReplenishmentReminderMessage()`. */
    @Synchronized
    fun randomReplenishment(medicationName: String, stockLevel: Int): ReminderMessage {
        val variants = replenishmentVariants(medicationName, stockLevel)
        val index = pick(variants.size, lastReplenishmentIndex)
        lastReplenishmentIndex = index
        return variants[index]
    }

    private fun pick(size: Int, last: Int): Int {
        var index = (0 until size).random()
        if (size > 1 && index == last) {
            index = (index + 1 + (0 until size - 1).random()) % size
        }
        return index
    }
}

package org.invest.bot.invest.core.modules.instruments;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum WhiteListOfShares {
    // --- Голубые фишки, основа российского рынка ---
    SBER("BBG004730N88", 10),    // Сбер Банк
    LKOH("BBG004731032", 1),     // ЛУКОЙЛ
    ROSN("BBG004731354", 1),     // Роснефть
    GMKN("BBG004731489", 1),     // Норильский никель
    NVTK("BBG00475KKY8", 1),     // НОВАТЭК
    TATN("BBG004S68829", 1),     // Татнефть
    //PLZL("BBG000R607Y3", 1),     // Полюс Золото
    //SNGS("BBG0047315D0", 100),   // Сургутнефтегаз (префы)
    MOEX("BBG004730JJ5", 10),    // Московская Биржа
    PHOR("BBG004S689R0", 1),     // ФосАгро
    ALRS("BBG004S68B31", 100),   // АЛРОСА
    IRAO("BBG004S68473", 1000),  // Интер РАО
    TRNFP("BBG00475KHX6", 1),    // Транснефть (префы)
    //VTBR("BBG004730ZJ9", 10000), // ВТБ
    //TCSG("TCS00A104T88", 10000), // TCS Group (Тинькофф)

    // --- Крупные и ликвидные металлурги ---
    CHMF("BBG00475K6C3", 1),     // Северсталь
    NLMK("BBG004S681B4", 10),    // НЛМК
    MAGN("BBG004S68507", 100),   // ММК

    // --- ИТ и Телеком ---
    YDEX("TCS00A107T19", 1),     // Яндекс
    MTSS("BBG004S681W1", 10),    // МТС
    POSI("TCS00A103X66", 1),     // Группа Позитив

    // --- Другие ликвидные компании ---
    //OZON("TCS00A1061L9", 1),     // Ozon
    FLOT("BBG000R04X57", 100),   // Совкомфлот
    AKRN("BBG004S684T9", 1),     // Акрон
    BSPB("BBG000QJW156", 100),   // Банк "Санкт-Петербург"
    NMTP("BBG004S68BR5", 1000);  // НМТП

    private final String figi;
    private final int correctLot;

    private static final Map<String, WhiteListOfShares> FIGI_TO_ENUM_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(WhiteListOfShares::getFigi, Function.identity()));

    WhiteListOfShares(String figi, int correctLot) {
        this.figi = figi;
        this.correctLot = correctLot;
    }

    public String getFigi() { return figi; }
    public int getCorrectLot() { return correctLot; }

    public static int getCorrectLot(String figi) {
        WhiteListOfShares share = FIGI_TO_ENUM_MAP.get(figi);
        return (share != null) ? share.getCorrectLot() : 1;
    }

    public static boolean isLotVerified(String figi) {
        return FIGI_TO_ENUM_MAP.containsKey(figi);
    }

}

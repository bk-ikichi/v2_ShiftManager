package jp.bk.shiftmanager.form;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 転記画面の1日分の入力。検証はServiceで行う */
@Data
public class ShiftDayForm {
    /** yyyy-MM-dd */
    private String date;
    /** 添字の欠番（「+ 追加する」の行など）には空の行またはnullが入る */
    private List<ShiftRowForm> rows = new ArrayList<>();
}

package jp.co.sss.lms.service;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 * @author 伊藤康秀 - Task.25
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {// ここで1="欠席", 2="遅刻"とかやってると予想
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * Task.25 過去日の未入力チェック
	 * @author 伊藤康秀 - Task.25
	 * @return 真偽値boolean
	 */
	public Boolean notEnterCheck() {

		try {
			
			String todayStr2 = dateUtil.toString(new Date(), "yyyy年MM月dd日");
			Date todayDate = dateUtil.parse(todayStr2);
			
			int nECount = tStudentAttendanceMapper.notEnterCount(
					loginUserDto.getLmsUserId(), Constants.DB_FLG_FALSE, todayDate);
		
			return 0 < nECount ;
		}catch(ParseException e){
			// sdf.parse()は例外処理として扱われるためれ
			e.printStackTrace();
			// エラー時
			return false;
		}
		
			
		
		/*
 * 
 * */
//		if (0 < nECount) {
//			return true;
//		} else {
//			return false;
//		}
		
	

	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			
			
			// @AutowiredでDI注入したUtilクラスは変数で呼び出す↓を修正TrainingTime trainingStartTime = trainingTime.
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */

	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
<<<<<<< HEAD
		
		attendanceForm.setHourMap(attendanceUtil.getHourMap());
		attendanceForm.setMinuteMap(attendanceUtil.getMinutesMap());
		
		
		System.out.print("Hello");
		Map<Integer, String> checkMap = attendanceForm.getHourMap();
		
		for(Map.Entry<Integer, String> e : checkMap.entrySet()) {
			Integer value = e.getKey();
			String str = e.getValue();
			System.out.println("来てるよ");
			System.out.println("値は："+ value + "文字は：" + str);		
=======
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			dailyAttendanceForm.setTrainingStartTime();

>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
		}

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

<<<<<<< HEAD
		
=======
		// とりま時間、分マップ書く

		Map<Integer, String> getHourMap = new LinkedHashMap<>();
		for (int i = 0; i < 24; i++) {

			getHourMap.put(i, String.format("%02d", i));
		}

		Map<Integer, String> getMinutesMap = new LinkedHashMap<>();
		for (int j = 0; j < 59; j++) {

			getMinutesMap.put(j, String.format("%02d", j));
		}
>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
<<<<<<< HEAD
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
=======

			if (attendanceManagementDto.getTrainingStartTime() != null) {
				dailyAttendanceForm
						.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			}

>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
			dailyAttendanceForm
<<<<<<< HEAD
					.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());			
=======
					.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());

>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}

<<<<<<< HEAD
			dailyAttendanceForm.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			
		
			
			// 勤怠Utilを使用して、分を切り出す
			String startTime = attendanceManagementDto.getTrainingStartTime();
			if(startTime != null && !startTime.isEmpty()) {
				
				//出勤時間の hh : mm 分割してセット
				dailyAttendanceForm.setTrainingStartTimeHour(attendanceUtil
						.getHour(attendanceManagementDto.getTrainingStartTime()));
				dailyAttendanceForm.setTrainingStartTimeMinute(attendanceUtil
						.getMinute(attendanceManagementDto.getTrainingStartTime()));
			}
			
			String endTime = attendanceManagementDto.getTrainingEndTime();
			if(endTime != null && !endTime.isEmpty()) {
				//退勤時間の hh : mm 分割してセット
				dailyAttendanceForm.setTrainingEndTimeHour(attendanceUtil
						.getHour(attendanceManagementDto.getTrainingEndTime()));
				dailyAttendanceForm.setTrainingEndTimeMinute(attendanceUtil
						.getMinute(attendanceManagementDto.getTrainingEndTime()));
			}

//				
				
				
				System.out.println("dailyAttendanceFormにsetした値:出勤時間"+dailyAttendanceForm
						.getTrainingStartTimeHour()+" : "+ dailyAttendanceForm.getTrainingStartTimeMinute());
				System.out.println("dailyAttendanceFormにsetした値:退勤時間"+dailyAttendanceForm
						.getTrainingEndTimeHour()+" : "+ dailyAttendanceForm.getTrainingEndTimeMinute());
			
			
			
=======
>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());
			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		} // ユーザー1人の勤怠情報1レコード分をfor分で繰り返してる

		return attendanceForm;
	}

	/**
	 * 勤怠管理直接編集
	 * 
	 * @author 伊藤康秀 - Task.25
	 * @param attendanceForm
<<<<<<< HEAD
	 * */

	public void formatConversion(AttendanceForm attendanceForm) {

		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			
				String setEmptyStr = "";
			
				Integer startTimeHour = dailyAttendanceForm.getTrainingStartTimeHour();
				Integer startTimeMinute = dailyAttendanceForm.getTrainingStartTimeMinute();
				
				if(startTimeHour != null && startTimeMinute !=null) {
					TrainingTime trainingTimeS = new TrainingTime(startTimeHour,startTimeMinute);
					dailyAttendanceForm.setTrainingStartTime(trainingTimeS.getFormattedString());
				}else {
					dailyAttendanceForm.setTrainingStartTime(setEmptyStr);
				}	
				
				Integer endTimeHour = dailyAttendanceForm.getTrainingEndTimeHour();
				Integer endTimeMinute = dailyAttendanceForm.getTrainingEndTimeMinute();

				if(endTimeHour != null && endTimeMinute !=null) {
					TrainingTime trainingTimeE = new TrainingTime(endTimeHour,endTimeMinute);
					dailyAttendanceForm.setTrainingEndTime(trainingTimeE.getFormattedString());
				}else {
					dailyAttendanceForm.setTrainingEndTime(setEmptyStr);
				}
			

		}

	}
	
	/**
	 * 入力チェック
	 * @author 伊藤康秀 - Task.27
	 * @param 
=======
>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
	 * */
<<<<<<< HEAD
	
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) {
		
		List<DailyAttendanceForm> userForm = attendanceForm.getAttendanceList();
		
		for(int i=0;i<userForm.size();i++) {
			// 備考の文字数が１００字以上だった時
			if(0 > userForm.get(i).getNote().length()
			  && userForm.get(i).getNote().length() > Constants.MAX_NOTE_LENGTH) {
				
				// JavaDocリファレンスより
				result.addError(new FieldError(
				result.getObjectName(),
						// ↓maybeここおかしいかも？
						"attendanceList[" + i + "].note",// オブジェクト名　定型記述
						messageUtil.getMessage(Constants.VALID_KEY_MAXBYTELENGTH,
								new String[] {"備考", "100"})// エラーメッセージの組み立て
						));
			}
			// 時刻の「hour」だけ、OR分の「minute」だけとかの片側未入力チェック
			// TrainingTimeクラスのisValidTrainingTimeがif文でつかえるかも？
			
			// 出勤時間のhour OR minuteで片側未入力
			if(userForm.get(i).getTrainingStartTimeHour() == null 
			  || userForm.get(i).getTrainingStartTimeMinute() == null) {
			
				result.addError(new FieldError(
				result.getObjectName(),
				 		"attendanceList[" + i + "].note",
				 		messageUtil.getMessage(Constants.INPUT_INVALID,
				 				new String[] {"出勤時間"})
					));
			}
			// 退勤時間のhour OR minuteで片側未入力
			if(userForm.get(i).getTrainingEndTimeHour() == null 
			  || userForm.get(i).getTrainingEndTimeMinute() == null) {
				
				result.addError(new FieldError(
				result.getObjectName(),
				 		"attendanceList[" + i + "].note",
				 		messageUtil.getMessage(Constants.INPUT_INVALID,
				 				new String[] {"退勤時間"})
								
					));
			}
			
			// 出勤時間が未入力（hour & minute）&&  退勤時間が入力有(hour & minute)
			if(null == userForm.get(i).getTrainingStartTimeHour() 
			  && null == userForm.get(i).getTrainingStartTimeMinute()
			  && userForm.get(i).getTrainingEndTimeHour() != null
			  && userForm.get(i).getTrainingEndTimeMinute() != null){
				
				result.addError(new FieldError(
				result.getObjectName(),
				 		"attendanceList[" + i + "].note",
				 		messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY)
					));
			}
			
			/*入力パラメータ．勤怠リスト[n]．出勤時間および退勤時間の両方に入力があり、
			 * 入力パラメータ．勤怠リスト[n]．出勤時間　＞　退勤時間　の場合、
			 * 下記エラーメッセージを追加設定
			 * */
			TrainingTime setTimeIn = new TrainingTime(userForm.get(i).getTrainingStartTimeHour(),
			userForm.get(i).getTrainingStartTimeMinute());
			
			String changeStrIn = setTimeIn.getFormattedString();
			TrainingTime setTimeOut = new TrainingTime(userForm.get(i).getTrainingStartTimeHour(),
			userForm.get(i).getTrainingStartTimeMinute());
			String changeStrOut = setTimeOut.getFormattedString();
			
			
			if(null != userForm.get(i).getTrainingStartTimeHour() 
			  && null != userForm.get(i).getTrainingStartTimeMinute()
			  && null != userForm.get(i).getTrainingEndTimeHour()
			  && null != userForm.get(i).getTrainingEndTimeMinute()
			  ){
				
				
				
				
				
=======

	public void formatConversion(AttendanceForm attendanceForm) {

		for (DailyAttendanceForm dailyAttendanceFormList : attendanceForm.getAttendanceList()) {

			if (null != dailyAttendanceFormList.getTrainingStartTime() &&
					null != dailyAttendanceFormList.getTrainingEndTime()) {

>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
			}
<<<<<<< HEAD
		
			
=======

>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
		}
<<<<<<< HEAD
		

		
=======

>>>>>>> branch 'main' of https://github.com/oneinabillion-sudo/lms-src-rp1-YasuhideIto.git
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

}

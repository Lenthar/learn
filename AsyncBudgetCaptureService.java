package jp.co.isid.platano.service.budget.management;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.Validator;

import jp.co.isid.aiuola.anakin.async.AsyncProcess;
import jp.co.isid.aiuola.anakin.async.AsyncProcessParamBase;
import jp.co.isid.aiuola.anakin.async.AsyncProcessStatus;
import jp.co.isid.aiuola.anakin.async.dto.AdditionalMessage;
import jp.co.isid.aiuola.anakin.async.endpoint.AsyncProcessResult;
import jp.co.isid.aiuola.anakin.doma.domain.JsonObject;
import jp.co.isid.aiuola.anakin.exception.AiuolaSystemException;
import jp.co.isid.aiuola.anakin.message.Message;
import jp.co.isid.aiuola.anakin.notification.NotificationParamBase;
import jp.co.isid.aiuola.anakin.notification.NotificationRequester;
import jp.co.isid.aiuola.anakin.updatelog.UpdatedLogging;
import jp.co.isid.aiuola.anakin.util.CollectionUtils;
import jp.co.isid.aiuola.anakin.util.ObjectUtils;
import jp.co.isid.aiuola.anakin.validation.ValidatorWithHint;
import jp.co.isid.aiuola.luke.dto.master.menu.MenuSummary;
import jp.co.isid.aiuola.luke.dto.master.role.RoleMenu;
import jp.co.isid.aiuola.luke.provider.MenuProvider;
import jp.co.isid.aiuola.luke.repository.master.role.RoleRepository;
import jp.co.isid.aiuola.luke.service.master.company.CompanyService;
import jp.co.isid.cosmo.common.AccountDivision;
import jp.co.isid.cosmo.common.FieldDivision;
import jp.co.isid.cosmo.context.security.DataSecurityAccesser;
import jp.co.isid.cosmo.datacontrol.validation.DataControlCsvValidator;
import jp.co.isid.cosmo.datacontrol.validation.DataControlCsvWarningValidator;
import jp.co.isid.cosmo.dto.autocomplete.AccountAutoCompleteItem;
import jp.co.isid.cosmo.dto.autocomplete.CompanyAutoCompleteItem;
import jp.co.isid.cosmo.dto.autocomplete.GenericMasterAutoCompleteItem;
import jp.co.isid.cosmo.dto.autocomplete.LedgerAutoCompleteItem;
import jp.co.isid.cosmo.dto.master.ledgertype.definition.Ledgertype;
import jp.co.isid.cosmo.dto.master.setting.companyparameter.CompanyParameter;
import jp.co.isid.cosmo.provider.CompanyCommonProvider.CompanyItems;
import jp.co.isid.cosmo.provider.FunctionCurrencyProvider;
import jp.co.isid.cosmo.provider.autocomplete.AccountAutoCompleteProvider;
import jp.co.isid.cosmo.provider.autocomplete.CompanyAutoCompleteProvider;
import jp.co.isid.cosmo.provider.autocomplete.GenericMasterAutoCompleteProvider;
import jp.co.isid.cosmo.provider.autocomplete.LedgerAutoCompleteProvider;
import jp.co.isid.cosmo.repository.master.setting.companyparameter.CompanyParameterRepository;
import jp.co.isid.platano.common.CaptureMode;
import jp.co.isid.platano.dto.budget.management.BudgetCaptureNotificationParam;
import jp.co.isid.platano.dto.budget.management.BudgetCaptureParam;
import jp.co.isid.platano.dto.budget.management.BudgetImportCsv;
import jp.co.isid.platano.dto.budget.management.BudgetImportCsv.ValidatorParam;
import jp.co.isid.platano.dto.budget.management.BudgetItemDto;
import jp.co.isid.platano.dto.budget.management.BudgetItemDtoForCsv;
import jp.co.isid.platano.dto.budget.management.BudgetLinkDataSummary;
import jp.co.isid.platano.repository.budget.management.BudgetEntryRepository;
import jp.co.isid.platano.repository.master.budget.definition.BudgetDefinitionRepository;
import jp.co.isid.platano.web.budget.management.mapper.BudgetAppMapper;
import jp.co.isid.platano.web.budget.management.validation.AccountCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.BudgetFieldsCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.BudgetScenariofindAndVersionStatusCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.CurrencyPrecisionCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.DocumentDateCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.FiscalPeriodCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.NotAccessCompanyCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.NotDuplicateBudgetItemCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.NotExistBudgetCompanyCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.NotExistBudgetCurrencyCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.NotExistCurrencyCodeCsvValidator;
import jp.co.isid.platano.web.budget.management.validation.PeriodTypeCsvValidator;

/**
 * 予算明細取込非同期処理のサービス.
 */
@Service("pl_asyncBudgetCaptureService")
@UpdatedLogging
public class AsyncBudgetCaptureService implements NotificationRequester {

    @Autowired
    private BudgetEntryRepository budgetEntryRepository;

    @Autowired
    private BudgetDefinitionRepository budgetDefinitionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CompanyParameterRepository companyParameterRepository;

    @Autowired
    private BudgetAppMapper budgetAppMapper;

    @Autowired
    private BudgetCaptureService budgetCaptureService;

    @Autowired
    private BudgetEntryService entryService;

    @Autowired
    private CompanyService companyService;

    @Autowired
    private GenericMasterAutoCompleteProvider genericMasterAutoCompleteProvider;

    /* 機能通貨Provider */
    @Autowired
    FunctionCurrencyProvider functionCurrencyProvider;

    @Autowired
    private LedgerAutoCompleteProvider ledgerAutoCompleteProvider;

    @Autowired
    private CompanyAutoCompleteProvider companyAutoCompleteProvider;

    @Autowired
    private MenuProvider menuProvider;

    // CSVインポート用データ制御ルールチェックバリデータ
    @Autowired
    private DataControlCsvValidator dataControlCsvValidator;

    // CSVインポート用データ制御ルールチェックバリデータ
    @Autowired
    private DataControlCsvWarningValidator dataControlCsvWarningValidator;

    // 会社存在チェック
    @Autowired
    private NotExistBudgetCompanyCsvValidator notExistBudgetCompanyCsvValidator;

    // 会社認可チェック
    @Autowired
    private NotAccessCompanyCsvValidator notAccessCompanyCsvValidator;

    // 予算シナリオバージョンの特定とバージョンのステータスチェック
    @Autowired
    private BudgetScenariofindAndVersionStatusCsvValidator budgetScenariofindAndVersionStatusCsvValidator;

    // 通貨・単位存在チェック
    @Autowired
    private NotExistCurrencyCodeCsvValidator notExistCurrencyCodeCsvValidator;

    // 通貨精度
    @Autowired
    private CurrencyPrecisionCsvValidator currencyPrecisionCsvValidator;

    // 勘定科目チェック、勘定科目認可チェック
    @Autowired
    private AccountCsvValidator accountCsvValidator;

    // 勘定科目以外のフィールドチェック
    @Autowired
    private BudgetFieldsCsvValidator budgetFieldsCsvValidator;

    // 入力フィールド組合せ重複チェック
    @Autowired
    private NotDuplicateBudgetItemCsvValidator notDuplicateBudgetItemCsvValidator;

    // 期間区分チェック
    @Autowired
    private FiscalPeriodCsvValidator fiscalPeriodCsvValidator;

    // 通貨-予算シナリオ通貨チェック
    @Autowired
    private NotExistBudgetCurrencyCsvValidator notExistBudgetCurrencyCsvValidator;

    // 伝票日付-会計期間整合性チェック
    @Autowired
    private DocumentDateCsvValidator documentDateCsvValidator;

    // 決算区分-会計期間整合性チェック
    @Autowired
    private PeriodTypeCsvValidator periodTypeCsvValidator;

    @Autowired
    private AccountAutoCompleteProvider accountAutoCompleteProvider;

    /**
     * 非同期処理を実行する
     *
     * @param params 非同期処理実行パラメータ
     * @return {@code AsyncProcessResult}
     * @throws ClassNotFoundException
     */
    @AsyncProcess
    @Transactional
    public AsyncProcessResult execution(JsonObject params) throws ClassNotFoundException {
        AsyncProcessParamBase tempParam = params.getObject(AsyncProcessParamBase.class);
        Class<?> originalClass = Class.forName(tempParam.getParamFQCN());
        BudgetCaptureParam param = (BudgetCaptureParam) params.getObject(originalClass);
        // 予算連携データチェックするためデータ取得
        // 为了检查预算协作数据而获取数据
        List<BudgetImportCsv> linkDataBySourceIdForCsv =
                budgetCaptureService.findLinkDataBySourceIdForCsv(param.getSourceId());

        AsyncProcessStatus asyncProcessStatus = AsyncProcessStatus.SUCCESS;
        if (CollectionUtils.isEmpty(linkDataBySourceIdForCsv)) {
            // インポート成功
            // 导入成功
            String successSummary = Message.getMessage("GL300I005");
            return createAsyncProcessResult(asyncProcessStatus, successSummary, null, param);
        }
        // エラーメッセージ
        // 错误信息
        List<String> errorMessages = null;
        // 会社存在チェック
        // 公司存在检查
        errorMessages = checkCompanyExists(param, linkDataBySourceIdForCsv);
        // 業務チェック＋CSVインポート用データ制御ルールチェックバリデータ（エラー）
        // 业务检查+CSV导入用数据控制规则检查验证器数据（错误）
        if (CollectionUtils.isEmpty(errorMessages)) {
            // 予算明細取込データチェック前処理
            // 预算明细取入数据检查预处理
            this.getValidatorParam(linkDataBySourceIdForCsv, param);
            // チェックを実施
            // 实施检查
            errorMessages = this.getErrorValidatorMessages(param, linkDataBySourceIdForCsv);
        }
        if (CollectionUtils.isNotEmpty(errorMessages)) {
            String errorSummary = Message.getMessage("GL300E018");
            asyncProcessStatus = AsyncProcessStatus.ERROR;
            return createAsyncProcessResult(asyncProcessStatus, errorSummary, errorMessages, param);
        }
        // CSVインポート用データ制御ルールチェックバリデータ（警告）
        // CSV导入用数据控制规则检查验证数据（警告）
        List<String> warningMessages =
                this.getWarningValidatorMessages(param, linkDataBySourceIdForCsv);
        if (CollectionUtils.isNotEmpty(warningMessages)) {
            asyncProcessStatus = AsyncProcessStatus.WARN;
        }
        // 予算連携データから予算明細へ連携するためデータ取得
        // 为了从预算联合数据联合到预算明细而获取数据
        List<BudgetLinkDataSummary> linkDataSummaries =
                budgetCaptureService.findBudgetLinkDataSummaries(param.getSourceId());
        List<BudgetItemDto> budgetLinkDataForDetail = new ArrayList<>();
        Long ledgerId = linkDataSummaries.get(0).getLedgerId();
        Long companyId = linkDataSummaries.get(0).getCompanyId();
        LocalDate referenceDate = linkDataSummaries.get(0).getBudgetReferenceDate();
        // 勘定科目情報取得
        List<AccountAutoCompleteItem> accountItems = accountAutoCompleteProvider
                .getAutoCompleteItemsWithDataSecurity(ledgerId, companyId, referenceDate, true);
        // 元帳フィールドテーブル情報
        // 分类帐字段表信息
        Ledgertype ledgertype = entryService.getLedgertypeField(ledgerId);
        //
        HashMap<Long, List<GenericMasterAutoCompleteItem>> genericMasterMap = new HashMap();
        // マスタ予備項目IDを取得し、設定
        // 获取主备项目ID并设置
        for (BudgetLinkDataSummary e : linkDataSummaries) {
            this.setMasterReservedFieldId(e, accountItems, ledgertype, genericMasterMap);
        }

        // 機能通貨取得
        // キー：元帳ID
        // 値：機能通貨ID
        Map<Long, Long> currencyIdMap =
                linkDataSummaries.stream().map(BudgetLinkDataSummary::getLedgerId).distinct()
                        .collect(Collectors.toMap(Function.identity(),
                                e -> functionCurrencyProvider.findCurrencyByLedger(e)));

        // 予算連携データの勘定科目区分により、借方金額、貸方金額を設定
        for (BudgetLinkDataSummary e : linkDataSummaries) {
            BudgetItemDto budgetItem = budgetAppMapper.toBudgetItemDto(e);
            String accountDiv = "";
            accountDiv = e.getAccountDiv();
            if (AccountDivision.ASSET.getValue().equals(accountDiv)
                    || AccountDivision.COST.getValue().equals(accountDiv)
                    || AccountDivision.OTHERS.getValue().equals(accountDiv)) {
                // 勘定科目.科目区分により 借方、貸方入れ替え
                budgetItem.setDebit(e.getAmount());// 借方▲
                budgetItem.setCredit(BigDecimal.ZERO);// 貸方▲
            } else if (AccountDivision.DEBT.getValue().equals(accountDiv)
                    || AccountDivision.NETASSET.getValue().equals(accountDiv)
                    || AccountDivision.PROFIT.getValue().equals(accountDiv)) {
                budgetItem = budgetAppMapper.toBudgetItemDto(e);
                // 勘定科目.科目区分により 借方、貸方入れ替え
                budgetItem.setDebit(BigDecimal.ZERO);// 借方▲
                budgetItem.setCredit(e.getAmount());// 貸方▲
            }

            Long currencyId = currencyIdMap.get(e.getLedgerId());
            // 換算後借方：通貨が機能通貨の場合、借方と同様、それ以外の場合、0
            if (currencyId != null && currencyId.equals(e.getCurrencyId())) {
                budgetItem.setDebitFcAmount(budgetItem.getDebit());// 換算後借方▲
                budgetItem.setCreditFcAmount(budgetItem.getCredit());// 換算後貸方▲
            } else {
                budgetItem.setDebitFcAmount(BigDecimal.ZERO);// 換算後借方▲
                budgetItem.setCreditFcAmount(BigDecimal.ZERO);// 換算後貸方▲
            }
            budgetLinkDataForDetail.add(budgetItem);
        }

        // 予算連携データから取込対象予算データの会社、元帳、予算シナリオID、通貨、会計期間をキーにしてデータを洗い出し
        // キー：会社、元帳、予算シナリオID、通貨、会計期間
        // 値：キー持つ予算連携データ（複数）

        //从预算合作数据中，以纳入对象预算数据的公司、分类帐、预算方案ID、货币、会计期间为键，提取数据
        //关键字：公司、分类帐、预算方案标识、币种、会计期
        //值：带按键的预算协作数据（多个）
        Map<String, List<BudgetItemDto>> budgetLinkDataMap = new HashMap<>();
        budgetLinkDataForDetail.forEach(item -> {
            String key =
                    item.getCompanyId() + "_" + item.getLedgerId() + "" + item.getBudgetScenarioId()
                            + "" + item.getCurrencyId() + "" + item.getFiscalPeriodId();
            if (budgetLinkDataMap.get(key) == null) {
                List<BudgetItemDto> budgetItemTemps = new ArrayList<>();
                budgetItemTemps.add(item);
                budgetLinkDataMap.put(key, budgetItemTemps);
            } else {
                List<BudgetItemDto> budgetItemTemps = budgetLinkDataMap.get(key);
                budgetItemTemps.add(item);
            }
        });
        // 会社、元帳、予算シナリオID、通貨、会計期間をキーで予算明細のデータ取得する
        // メモリ足りない可能なので、キーごとに予算明細TBから合致したいデータを取得

        //通过关键字获取公司、分类帐、预算方案标识、货币和会计期间的预算行数据
        //由于可能内存不足，所以按每个键从预算明细TB中获取想要匹配的数据
        for (String key : budgetLinkDataMap.keySet()) {
            List<BudgetItemDto> budgetLinkData = budgetLinkDataMap.get(key);
            BudgetItemDto budgetItemForSearchCondition = budgetLinkData.get(0);
            Map<String, BudgetItemDtoForCsv> budgetItemMap = new HashMap<>();
            List<BudgetItemDtoForCsv> budgetItemDtoForCsvs = budgetEntryRepository
                    .findAllBudgetItemsByCondition(budgetItemForSearchCondition);
            for (BudgetItemDtoForCsv summary : budgetItemDtoForCsvs) {
                BudgetItemDto dto = budgetAppMapper.toBudgetItemDto(summary);
                String budgetLinkDataForDetailtr = formatFormData(dto);
                budgetItemMap.put(budgetLinkDataForDetailtr, summary);
            }
            // 連携データは更新データか新規データか判断処理
            // 协作数据是更新数据还是新数据的判断处理
            for (BudgetItemDto item : budgetLinkData) {
                String linkItemKeyStr = formatFormData(item);
                if (budgetItemMap.containsKey(linkItemKeyStr)) {
                    item.setUpdateFlag(true);
                    item.setBudgetItemId(budgetItemMap.get(linkItemKeyStr).getBudgetItemId());
                    item.setVersionNo(budgetItemMap.get(linkItemKeyStr).getVersionNo());
                    // 予算取込処理かつ、取込モードが「加算」の場合
                    // 预算取入处理且取入模式为“相加”时
                    if (Objects.equals(CaptureMode.CAPTURE_MODE_ADD.getValue(),
                            item.getCaptureMode())) {
                        item.setDebit(
                                budgetItemMap.get(linkItemKeyStr).getDebit().add(item.getDebit()));// 借方▲
                        item.setCredit(budgetItemMap.get(linkItemKeyStr).getCredit()
                                .add(item.getCredit()));// 貸方▲
                        item.setDebitFcAmount(budgetItemMap.get(linkItemKeyStr).getDebitFcAmount()
                                .add(item.getDebitFcAmount()));// 換算後借方▲
                        item.setCreditFcAmount(budgetItemMap.get(linkItemKeyStr).getCreditFcAmount()
                                .add(item.getCreditFcAmount()));// 換算後貸方▲
                    }
                }
            }
            budgetItemMap.clear();
        }
        // Bellaへ連携必要な予算バージョンセット（重複しない）
        // 需要与Bella协作的预算版本集（不重复）
        HashSet<Long> bellalinkBudgetVersionSet = new HashSet<>();
        // 予算明細データ登録・更新、及びBellaへ連携必要な予算バージョンを洗い出し
        // 预算明细数据登录、更新，以及与Bella合作的必要预算版本
        this.mergeLinkDataToBudget(budgetLinkDataForDetail, bellalinkBudgetVersionSet);
        // 予算明細取込の後処理(ベラ―連携、予算連携データ削除)
        // 预算明细的后处理（委-合作、删除预算合作数据）
        this.afterImport(param.getSourceId(), bellalinkBudgetVersionSet);
        // 処理結果メッセージ
        // 处理结果消息
        String successSummary = Objects.equals(asyncProcessStatus, AsyncProcessStatus.SUCCESS)
                ? Message.getMessage("GL300I005")
                : Message.getMessage("GL300I007");
        return createAsyncProcessResult(asyncProcessStatus, successSummary, warningMessages, param);
    }

    /**
     * 認証用データを取得する
     *
     * @param csvs 予算明細のcsv情報
     * @param param パラメータ
     */
    private void getValidatorParam(List<BudgetImportCsv> csvs, BudgetCaptureParam param) {
        // 認証用マップ
        // キー：会社コード
        // 値：認証用パラメータ
        Map<String, ValidatorParam> validatorParamMap = new HashMap<>();
        if (param.getReferenceData() != null) {
            ValidatorParam validatorParam = new ValidatorParam();
            validatorParam.setCompanyList(param.getCompanyList());
            validatorParam.setLedgerList(param.getLedgerList());
            validatorParam.setCompanyMap(param.getCompanyMap());
            validatorParam.setRoleId(param.getRoleId());
            validatorParam.setMenuId(param.getMenuId());
            validatorParamMap.put(csvs.get(0).getCompanyCd(), validatorParam);
        } else {
            // 基準日
            param.setReferenceData(
                    ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime().toLocalDate());
            validatorParamMap = csvs.stream().map(BudgetImportCsv::getCompanyCd).distinct()
                    .collect(Collectors.toMap(Function.identity(), companyCd -> {
                        ValidatorParam validatorParam = new ValidatorParam();
                        Long companyId = companyService.findCompany(companyCd).getCompanyId();
                        // ロールID
                        Optional<CompanyParameter> companyParameter = companyParameterRepository
                                .findByCompanyId(companyId, param.getReferenceData());
                        if (!companyParameter.isPresent()) {
                            return validatorParam;
                        }
                        validatorParam.setRoleId(companyParameter.get()
                                .getCompanyParameterFinancials().getJournalImportRoleValue());
                        // メニューID
                        // ロールメニュー取得
                        List<RoleMenu> roleMenus =
                                roleRepository.findRoleMenu(validatorParam.getRoleId());
                        // メニュー取得
                        Long menuId = roleMenus.get(0).getMenuDefId();
                        List<MenuSummary> menus = menuProvider.getList();
                        List<Long> roleMenuIds = roleMenus.stream().map(RoleMenu::getMenuDefId)
                                .collect(Collectors.toList());
                        MenuSummary defaultMenu = menus.stream()
                                .filter(menu -> menu.isDefaultMenuFlg()
                                        && roleMenuIds.contains(menu.getMenuId()))
                                .findFirst().orElse(null);
                        if (defaultMenu != null) {
                            menuId = defaultMenu.getMenuId();
                        }
                        validatorParam.setMenuId(menuId);

                        // 公開範囲会社リスト （バッチにセッションがないため、CSV取り込み指示ユーザに対する公開範囲リスト取得できないため、同期側から渡すこと）
                        List<CompanyItems> companies =
                                budgetCaptureService.getCompanyList(validatorParam.getRoleId(),
                                        validatorParam.getMenuId(), param.getReferenceData());
                        validatorParam.setCompanyList(companies);

                        // 有効な会社リスト
                        List<Long> accessibleCompanyIds = validatorParam.getCompanyList().stream()
                                .map(item -> item.getCompanyId()).collect(Collectors.toList());

                        // ロールに認可のデータ公開範囲（参照）で割り当てられた元帳から選択可能
                        List<LedgerAutoCompleteItem> ledgerAutoCompleteItems =
                                ledgerAutoCompleteProvider.getAutoCompleteItemsWithDataSecurity(
                                        validatorParam.getRoleId(), validatorParam.getMenuId(),
                                        accessibleCompanyIds);
                        validatorParam.setLedgerList(ledgerAutoCompleteItems);

                        // 元帳・会社の組み合わせ
                        Map<String, List<CompanyAutoCompleteItem>> companyMap = new HashMap<>();
                        for (int i = 0; i < ledgerAutoCompleteItems.size(); i++) {
                            List<CompanyAutoCompleteItem> companyItems = companyAutoCompleteProvider
                                    .getAutoCompleteItemsWithDataSecurity(
                                            validatorParam.getRoleId(), validatorParam.getMenuId(),
                                            ledgerAutoCompleteItems.get(i).getId(),
                                            accessibleCompanyIds, param.getReferenceData());
                            companyMap.put(ledgerAutoCompleteItems.get(i).getCode(), companyItems);
                        }
                        validatorParam.setCompanyMap(companyMap);
                        return validatorParam;
                    }));
        }

        for (int i = 0; i < csvs.size(); i++) {
            BudgetImportCsv budgetImportCsv = csvs.get(i);
            budgetImportCsv
                    .setValidatorParam(validatorParamMap.get(budgetImportCsv.getCompanyCd()));
        }
    }

    /**
     * 予算明細取込と通知の関連処理
     *
     * @param param パラメータ
     */
    private void budgetCaptureNotification(BudgetCaptureParam param) {

        // 予算明細取込正常終了
        // 预算明细取入正常结束
        BudgetCaptureNotificationParam budgetCaptureNotificationParam =
                new BudgetCaptureNotificationParam(
                        BudgetCaptureNotificationParam.PRODUCT_SUCCESS_ID,
                        BudgetCaptureNotificationParam.EVENT_SUCCESS_CODE);
        //如果没有 通知对像，则返回
        if (CollectionUtils.isEmpty(param.getReceiverUserIds())) {
            return;
        }
        LocalDateTime currentDateTime = ZonedDateTime.now(ZoneId.systemDefault()).toLocalDateTime();
        budgetCaptureNotificationParam.setReferenceDate(currentDateTime.toLocalDate()); // 基準日
        budgetCaptureNotificationParam.setReceiverUserIds(param.getReceiverUserIds()); // 通知先
        budgetCaptureNotificationParam.setAsyncProcessId(param.getProcessId()); // 処理ID
        budgetCaptureNotificationParam.setExecutionTime(currentDateTime); // 実行日時

        NotificationParamBase baseParam =
                NotificationParamBase.class.cast(budgetCaptureNotificationParam);
        addNotification(param.getFunctionId(), baseParam);

    }

    /**
     * マスタ予備項目IDを取得し、設定
     * 
     * @param e 検索用連携データ
     */
    private void setMasterReservedFieldId(BudgetLinkDataSummary e,
            List<AccountAutoCompleteItem> accountItems, Ledgertype ledgertype,
            HashMap<Long, List<GenericMasterAutoCompleteItem>> genericMasterMap) {
        if (e.getBudgetItemMasterReservedField1() != null
                && !e.getBudgetItemMasterReservedField1().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER1, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField1()))
                    .forEach(i -> e.setMasterReservedField1Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField2() != null
                && !e.getBudgetItemMasterReservedField2().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER2, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField2()))
                    .forEach(i -> e.setMasterReservedField2Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField3() != null
                && !e.getBudgetItemMasterReservedField3().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER3, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField3()))
                    .forEach(i -> e.setMasterReservedField3Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField4() != null
                && !e.getBudgetItemMasterReservedField4().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER4, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField4()))
                    .forEach(i -> e.setMasterReservedField4Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField5() != null
                && !e.getBudgetItemMasterReservedField5().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER5, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField5()))
                    .forEach(i -> e.setMasterReservedField5Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField6() != null
                && !e.getBudgetItemMasterReservedField6().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER6, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField6()))
                    .forEach(i -> e.setMasterReservedField6Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField7() != null
                && !e.getBudgetItemMasterReservedField7().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER7, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField7()))
                    .forEach(i -> e.setMasterReservedField7Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField8() != null
                && !e.getBudgetItemMasterReservedField8().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER8, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField8()))
                    .forEach(i -> e.setMasterReservedField8Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField9() != null
                && !e.getBudgetItemMasterReservedField9().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER9, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField9()))
                    .forEach(i -> e.setMasterReservedField9Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField10() != null
                && !e.getBudgetItemMasterReservedField10().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER10, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField10()))
                    .forEach(i -> e.setMasterReservedField10Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField11() != null
                && !e.getBudgetItemMasterReservedField11().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER11, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField11()))
                    .forEach(i -> e.setMasterReservedField11Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField12() != null
                && !e.getBudgetItemMasterReservedField12().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER12, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField12()))
                    .forEach(i -> e.setMasterReservedField12Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField13() != null
                && !e.getBudgetItemMasterReservedField13().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER13, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField13()))
                    .forEach(i -> e.setMasterReservedField13Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField14() != null
                && !e.getBudgetItemMasterReservedField14().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER14, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField14()))
                    .forEach(i -> e.setMasterReservedField14Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField15() != null
                && !e.getBudgetItemMasterReservedField15().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER15, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField15()))
                    .forEach(i -> e.setMasterReservedField15Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField16() != null
                && !e.getBudgetItemMasterReservedField16().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER16, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField16()))
                    .forEach(i -> e.setMasterReservedField16Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField17() != null
                && !e.getBudgetItemMasterReservedField17().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER17, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField17()))
                    .forEach(i -> e.setMasterReservedField17Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField18() != null
                && !e.getBudgetItemMasterReservedField18().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER18, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField18()))
                    .forEach(i -> e.setMasterReservedField18Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField19() != null
                && !e.getBudgetItemMasterReservedField19().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER19, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField19()))
                    .forEach(i -> e.setMasterReservedField19Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField20() != null
                && !e.getBudgetItemMasterReservedField20().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER20, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField20()))
                    .forEach(i -> e.setMasterReservedField20Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField21() != null
                && !e.getBudgetItemMasterReservedField21().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER21, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField21()))
                    .forEach(i -> e.setMasterReservedField21Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField22() != null
                && !e.getBudgetItemMasterReservedField22().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER22, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField22()))
                    .forEach(i -> e.setMasterReservedField22Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField23() != null
                && !e.getBudgetItemMasterReservedField23().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER23, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField23()))
                    .forEach(i -> e.setMasterReservedField23Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField24() != null
                && !e.getBudgetItemMasterReservedField24().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER24, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField24()))
                    .forEach(i -> e.setMasterReservedField24Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField25() != null
                && !e.getBudgetItemMasterReservedField25().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = genericMasterAutoCompleteProvider
                    .getAutoCompleteItemsWithDataSecurity(e.getLedgerId(), e.getCompanyId(),
                            FieldDivision.LINE_GENERAL_MASTER25, e.getBudgetReferenceDate(), true);
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField25()))
                    .forEach(i -> e.setMasterReservedField25Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField26() != null
                && !e.getBudgetItemMasterReservedField26().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = new ArrayList<>();
            // 勘定科目依存項目判定
            boolean isAccountDependentItemFlag = entryService.isAccountDependentItemFlag(ledgertype,
                    FieldDivision.LINE_GENERAL_MASTER26.getValue());
            if (isAccountDependentItemFlag && ObjectUtils.isNotEmpty(e.getAccountId())) {
                Long genericMasterDefId = this.getGenericMasterDefId(e.getAccountId(),
                        FieldDivision.LINE_GENERAL_MASTER26.getValue(), accountItems);
                if (genericMasterDefId != null) {
                    if (!genericMasterMap.containsKey(genericMasterDefId)) {
                        items = genericMasterAutoCompleteProvider
                                .getAutoCompleteItemForAccountDependent(genericMasterDefId,
                                        e.getBudgetReferenceDate(), null);
                        genericMasterMap.put(genericMasterDefId, items);
                    } else {
                        items = genericMasterMap.get(genericMasterDefId);
                    }
                }
            } else {
                items = genericMasterAutoCompleteProvider.getAutoCompleteItemsWithDataSecurity(
                        e.getLedgerId(), e.getCompanyId(), FieldDivision.LINE_GENERAL_MASTER26,
                        e.getBudgetReferenceDate(), true);
            }
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField26()))
                    .forEach(i -> e.setMasterReservedField26Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField27() != null
                && !e.getBudgetItemMasterReservedField27().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = new ArrayList<>();
            // 勘定科目依存項目判定
            boolean isAccountDependentItemFlag = entryService.isAccountDependentItemFlag(ledgertype,
                    FieldDivision.LINE_GENERAL_MASTER27.getValue());
            if (isAccountDependentItemFlag && ObjectUtils.isNotEmpty(e.getAccountId())) {
                Long genericMasterDefId = this.getGenericMasterDefId(e.getAccountId(),
                        FieldDivision.LINE_GENERAL_MASTER27.getValue(), accountItems);
                if (genericMasterDefId != null) {
                    if (!genericMasterMap.containsKey(genericMasterDefId)) {
                        items = genericMasterAutoCompleteProvider
                                .getAutoCompleteItemForAccountDependent(genericMasterDefId,
                                        e.getBudgetReferenceDate(), null);
                        genericMasterMap.put(genericMasterDefId, items);
                    } else {
                        items = genericMasterMap.get(genericMasterDefId);
                    }
                }
            } else {
                items = genericMasterAutoCompleteProvider.getAutoCompleteItemsWithDataSecurity(
                        e.getLedgerId(), e.getCompanyId(), FieldDivision.LINE_GENERAL_MASTER27,
                        e.getBudgetReferenceDate(), true);
            }
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField27()))
                    .forEach(i -> e.setMasterReservedField27Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField28() != null
                && !e.getBudgetItemMasterReservedField28().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = new ArrayList<>();
            // 勘定科目依存項目判定
            boolean isAccountDependentItemFlag = entryService.isAccountDependentItemFlag(ledgertype,
                    FieldDivision.LINE_GENERAL_MASTER28.getValue());
            if (isAccountDependentItemFlag && ObjectUtils.isNotEmpty(e.getAccountId())) {
                Long genericMasterDefId = this.getGenericMasterDefId(e.getAccountId(),
                        FieldDivision.LINE_GENERAL_MASTER28.getValue(), accountItems);
                if (genericMasterDefId != null) {
                    if (!genericMasterMap.containsKey(genericMasterDefId)) {
                        items = genericMasterAutoCompleteProvider
                                .getAutoCompleteItemForAccountDependent(genericMasterDefId,
                                        e.getBudgetReferenceDate(), null);
                        genericMasterMap.put(genericMasterDefId, items);
                    } else {
                        items = genericMasterMap.get(genericMasterDefId);
                    }
                }
            } else {
                items = genericMasterAutoCompleteProvider.getAutoCompleteItemsWithDataSecurity(
                        e.getLedgerId(), e.getCompanyId(), FieldDivision.LINE_GENERAL_MASTER28,
                        e.getBudgetReferenceDate(), true);
            }
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField28()))
                    .forEach(i -> e.setMasterReservedField28Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField29() != null
                && !e.getBudgetItemMasterReservedField29().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = new ArrayList<>();
            // 勘定科目依存項目判定
            boolean isAccountDependentItemFlag = entryService.isAccountDependentItemFlag(ledgertype,
                    FieldDivision.LINE_GENERAL_MASTER29.getValue());
            if (isAccountDependentItemFlag && ObjectUtils.isNotEmpty(e.getAccountId())) {
                Long genericMasterDefId = this.getGenericMasterDefId(e.getAccountId(),
                        FieldDivision.LINE_GENERAL_MASTER29.getValue(), accountItems);
                if (genericMasterDefId != null) {
                    if (!genericMasterMap.containsKey(genericMasterDefId)) {
                        items = genericMasterAutoCompleteProvider
                                .getAutoCompleteItemForAccountDependent(genericMasterDefId,
                                        e.getBudgetReferenceDate(), null);
                        genericMasterMap.put(genericMasterDefId, items);
                    } else {
                        items = genericMasterMap.get(genericMasterDefId);
                    }
                }
            } else {
                items = genericMasterAutoCompleteProvider.getAutoCompleteItemsWithDataSecurity(
                        e.getLedgerId(), e.getCompanyId(), FieldDivision.LINE_GENERAL_MASTER29,
                        e.getBudgetReferenceDate(), true);
            }
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField29()))
                    .forEach(i -> e.setMasterReservedField29Id(i.getId()));
        }
        if (e.getBudgetItemMasterReservedField30() != null
                && !e.getBudgetItemMasterReservedField30().trim().isEmpty()) {
            List<GenericMasterAutoCompleteItem> items = new ArrayList<>();
            // 勘定科目依存項目判定
            boolean isAccountDependentItemFlag = entryService.isAccountDependentItemFlag(ledgertype,
                    FieldDivision.LINE_GENERAL_MASTER30.getValue());
            if (isAccountDependentItemFlag && ObjectUtils.isNotEmpty(e.getAccountId())) {
                Long genericMasterDefId = this.getGenericMasterDefId(e.getAccountId(),
                        FieldDivision.LINE_GENERAL_MASTER30.getValue(), accountItems);
                if (genericMasterDefId != null) {
                    if (!genericMasterMap.containsKey(genericMasterDefId)) {
                        items = genericMasterAutoCompleteProvider
                                .getAutoCompleteItemForAccountDependent(genericMasterDefId,
                                        e.getBudgetReferenceDate(), null);
                        genericMasterMap.put(genericMasterDefId, items);
                    } else {
                        items = genericMasterMap.get(genericMasterDefId);
                    }
                }
            } else {
                items = genericMasterAutoCompleteProvider.getAutoCompleteItemsWithDataSecurity(
                        e.getLedgerId(), e.getCompanyId(), FieldDivision.LINE_GENERAL_MASTER30,
                        e.getBudgetReferenceDate(), true);
            }
            items.stream().filter(item -> item.code.equals(e.getBudgetItemMasterReservedField30()))
                    .forEach(i -> e.setMasterReservedField30Id(i.getId()));
        }
    }

    /**
     * 勘定科目/勘定科目依存項目(汎用マスタ定義ID)を取得
     * 
     * @param accountId 勘定科目ID
     * @param fieldDivision 会社フィールド区分
     * @return 勘定科目/勘定科目依存項目(汎用マスタ定義ID)
     */
    public Long getGenericMasterDefId(Long accountId, String fieldDivision,
            List<AccountAutoCompleteItem> accountItems) {
        if (accountItems != null) {
            AccountAutoCompleteItem selectedAccountAutoCompleteItem = accountItems.stream()
                    .filter(d -> Objects.equals(accountId, d.getId())).findAny().get();
            if (selectedAccountAutoCompleteItem != null) {
                if (FieldDivision.LINE_GENERAL_MASTER26.getValue().equals(fieldDivision)) {
                    return selectedAccountAutoCompleteItem.getAccountDependentItem1();
                } else if (FieldDivision.LINE_GENERAL_MASTER27.getValue().equals(fieldDivision)) {
                    return selectedAccountAutoCompleteItem.getAccountDependentItem2();
                } else if (FieldDivision.LINE_GENERAL_MASTER28.getValue().equals(fieldDivision)) {
                    return selectedAccountAutoCompleteItem.getAccountDependentItem3();
                } else if (FieldDivision.LINE_GENERAL_MASTER29.getValue().equals(fieldDivision)) {
                    return selectedAccountAutoCompleteItem.getAccountDependentItem4();
                } else if (FieldDivision.LINE_GENERAL_MASTER30.getValue().equals(fieldDivision)) {
                    return selectedAccountAutoCompleteItem.getAccountDependentItem5();
                }
            }
        }
        return null;
    }

    /**
     * 予算明細データ登録・更新、及びBellaへ連携必要な予算バージョンを洗い出し
     *
     * @param budgetItemDtos 予算明細データ
     * @param bellalinkBudgetVersionSet Bellaへ連携必要な予算バージョンセット
     */
    private void mergeLinkDataToBudget(List<BudgetItemDto> budgetItemDtos,
            HashSet<Long> bellalinkBudgetVersionSet) {
        List<BudgetItemDto> budgetDatasForUpdate = new ArrayList<>();
        List<BudgetItemDto> budgetDatasForInsert = new ArrayList<>();
        for (BudgetItemDto budgetItemDto : budgetItemDtos) {
            bellalinkBudgetVersionSet.add(budgetItemDto.getBudgetVersionId());
            if (budgetItemDto.isUpdateFlag()) {
                budgetDatasForUpdate.add(budgetItemDto);
            } else {
                budgetDatasForInsert.add(budgetItemDto);
            }
        }
        // 予算明細テーブル一括更新処理
        // 预算明细表成批更新处理
        if (CollectionUtils.isNotEmpty(budgetDatasForUpdate)) {
            entryService.updateBudgetItems(budgetDatasForUpdate);
        }
        // 予算明細テーブル一括新規登録処理
        // 预算明细表批量新注册处理
        if (CollectionUtils.isNotEmpty(budgetDatasForInsert)) {
            entryService.insertBudgetItems(budgetDatasForInsert);
        }
    }


    /**
     * 予算明細のデータを比較用にフォーマットする。
     * 
     * @param value 予算明細データ
     * @return レスポンスエンティティ
     */
    private String formatFormData(BudgetItemDto value) {
        String lineValue = null;
        lineValue = String.valueOf(value.getBudgetVersionId());// 予算バージョンID
        lineValue += "," + String.valueOf(value.getCompanyId());// 会社ID
        lineValue += "," + String.valueOf(value.getCurrencyId());// 通貨・単位
        lineValue += "," + String.valueOf(value.getFiscalPeriodId());// 期間
        lineValue += "," + String.valueOf(value.getDocumentDate());// 伝票日付
        lineValue += "," + String.valueOf(value.getPeriodTypeId());// 決算区分ID
        lineValue += "," + String.valueOf(value.getAccountingUnitId());// 会計単位ID
        lineValue += "," + String.valueOf(value.getAccountId());// 勘定科目ID
        lineValue += "," + String.valueOf(value.getBankAccountId()); // 預金口座
        lineValue += "," + String.valueOf(value.getCostCenterId());// 部門ID
        lineValue += "," + String.valueOf(value.getProjectId());// プロジェクトID
        lineValue += "," + String.valueOf(value.getBizPtnrBasicInfoId());// 取引先基本情報ID
        lineValue += "," + String.valueOf(value.getGroupCompanyId());// グループ会社ID
        lineValue += "," + String.valueOf(value.getIncreaseDecreaseDivisionId());// 増減区分ID
        lineValue += "," + String.valueOf(value.getJournalDetailDescription());// 仕訳明細摘要
        lineValue += "," + String.valueOf(value.getTextReservedField1());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField2());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField3());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField4());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField5());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField6());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField7());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField8());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField9());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getTextReservedField10());// テキスト予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField1());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField2());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField3());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField4());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField5());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField6());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField7());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField8());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField9());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getNumericReservedField10());// 数値予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField1());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField2());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField3());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField4());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField5());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField6());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField7());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField8());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField9());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getDateReservedField10());// 日付予備項目（明細）
        lineValue += "," + String.valueOf(value.getMasterReservedField1());// マスタ予備項目（明細）
        lineValue += "," + String.valueOf(value.getMasterReservedField2());
        lineValue += "," + String.valueOf(value.getMasterReservedField3());
        lineValue += "," + String.valueOf(value.getMasterReservedField4());
        lineValue += "," + String.valueOf(value.getMasterReservedField5());
        lineValue += "," + String.valueOf(value.getMasterReservedField6());
        lineValue += "," + String.valueOf(value.getMasterReservedField7());
        lineValue += "," + String.valueOf(value.getMasterReservedField8());
        lineValue += "," + String.valueOf(value.getMasterReservedField9());
        lineValue += "," + String.valueOf(value.getMasterReservedField10());
        lineValue += "," + String.valueOf(value.getMasterReservedField11());
        lineValue += "," + String.valueOf(value.getMasterReservedField12());
        lineValue += "," + String.valueOf(value.getMasterReservedField13());
        lineValue += "," + String.valueOf(value.getMasterReservedField14());
        lineValue += "," + String.valueOf(value.getMasterReservedField15());
        lineValue += "," + String.valueOf(value.getMasterReservedField16());
        lineValue += "," + String.valueOf(value.getMasterReservedField17());
        lineValue += "," + String.valueOf(value.getMasterReservedField18());
        lineValue += "," + String.valueOf(value.getMasterReservedField19());
        lineValue += "," + String.valueOf(value.getMasterReservedField20());
        lineValue += "," + String.valueOf(value.getMasterReservedField21());
        lineValue += "," + String.valueOf(value.getMasterReservedField22());
        lineValue += "," + String.valueOf(value.getMasterReservedField23());
        lineValue += "," + String.valueOf(value.getMasterReservedField24());
        lineValue += "," + String.valueOf(value.getMasterReservedField25());
        lineValue += "," + String.valueOf(value.getMasterReservedField26());
        lineValue += "," + String.valueOf(value.getMasterReservedField27());
        lineValue += "," + String.valueOf(value.getMasterReservedField28());
        lineValue += "," + String.valueOf(value.getMasterReservedField29());
        lineValue += "," + String.valueOf(value.getMasterReservedField30());
        return lineValue;
    }

    /**
     * 予算明細取込の後処理
     *
     * @param sourceId ソースID
     * @param bellalinkBudgetVersionSet Bellaへ連携必要な予算バージョンセット
     */
    private void afterImport(Long sourceId, HashSet<Long> bellalinkBudgetVersionSet) {
        for (Long bellalinkBudgetVersionId : bellalinkBudgetVersionSet) {
            // 連携日時がNULLにする
            // 协作时间为空
            budgetDefinitionRepository.updateBudgetVersionLinkDatetime(bellalinkBudgetVersionId);
        }
        // 予算連携データ削除処理
        // 预算联合数据删除处理
        budgetCaptureService.deleteBudgetLinkData(sourceId);
    }

    /**
     * バリデータによるメッセージを取得する
     *
     * @param validatorsWithHints Validatorクラス
     * @param csvs CSVデータ
     * @return メッセージ
     */
    private List<String> getMessageByValidatorParams(List<ValidatorWithHint> validatorsWithHints,
            List<BudgetImportCsv> csvs) {
        BindingResult bindingResult = new BeanPropertyBindingResult(null, "csv");
        for (ValidatorWithHint validatorsWithHint : validatorsWithHints) {
            Validator validator = validatorsWithHint.getValidator();
            List<Class<?>> interfaces = Arrays.asList(validator.getClass().getInterfaces());
            Method method = null;
            try {
                if (interfaces.contains(SmartValidator.class)
                        || validator instanceof BudgetFieldsCsvValidator) {
                    method = validator.getClass().getMethod("validate", Object.class, Errors.class,
                            Object[].class);
                    method.invoke(validator, csvs, bindingResult, validatorsWithHint.getHints());
                } else {
                    method = validator.getClass().getMethod("validate", Object.class, Errors.class);
                    method.invoke(validator, csvs, bindingResult);
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                throw new AiuolaSystemException("GL825C005", e);
            }
        }
        return bindingResult.getAllErrors().stream().map(ObjectError::getDefaultMessage)
                .collect(Collectors.toList());
    }

    /**
     * 警告メッセージを取得する
     * 
     * @param param パラメータ
     * @param csvs CSV情報
     * @return 警告メッセージ
     */
    private List<String> getWarningValidatorMessages(BudgetCaptureParam param,
            List<BudgetImportCsv> csvs) {
        List<ValidatorWithHint> warningValidators = new ArrayList<>();
        // CSVインポート用データ制御ルールチェックバリデータ
        warningValidators
                .add(new ValidatorWithHint(dataControlCsvWarningValidator, new Object[] {}));
        return this.getMessageByValidatorParams(warningValidators, csvs);
    }

    /**
     * エラーメッセージを取得する
     *
     * @param param パラメータ
     * @param csvs CSV情報
     * @return エラーメッセージ
     */
    private List<String> getErrorValidatorMessages(BudgetCaptureParam param,
            List<BudgetImportCsv> csvs) {
        List<ValidatorWithHint> errorValidators = new ArrayList<>();
        errorValidators.add(new ValidatorWithHint(notAccessCompanyCsvValidator, // 会社認可チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(budgetScenariofindAndVersionStatusCsvValidator, // 予算シナリオバージョンの特定とバージョンのステータスチェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(notExistCurrencyCodeCsvValidator, // 通貨・単位存在チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(currencyPrecisionCsvValidator, // 通貨精度
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(accountCsvValidator, // 勘定科目チェック、勘定科目認可チェック
                new Object[] {}));
        errorValidators.add(DataSecurityAccesser
                .createDataSecurityCsvValidatorWithHint(budgetFieldsCsvValidator, new Object[] {}));// 勘定科目以外のフィールドチェック
        errorValidators.add(new ValidatorWithHint(notDuplicateBudgetItemCsvValidator, // 入力フィールド組合せ重複チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(fiscalPeriodCsvValidator, // 期間区分チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(notExistBudgetCurrencyCsvValidator, // 通貨-予算シナリオ通貨チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(documentDateCsvValidator, // 伝票日付-会計期間整合性チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(periodTypeCsvValidator, // 決算区分-会計期間整合性チェック
                new Object[] {}));
        errorValidators.add(new ValidatorWithHint(dataControlCsvValidator, // CSVインポート用データ制御ルールチェックバリデータ
                new Object[] {}));
        return this.getMessageByValidatorParams(errorValidators, csvs);
    }

    /**
     * エラーメッセージを取得する（会社存在チェック）
     * 
     * @param param パラメータ
     * @param csvs CSV情報
     * @return エラーメッセージ
     */
    private List<String> checkCompanyExists(BudgetCaptureParam param, List<BudgetImportCsv> csvs) {
        List<ValidatorWithHint> errorValidators = new ArrayList<>();
        errorValidators.add(new ValidatorWithHint(notExistBudgetCompanyCsvValidator, // 会社存在チェック
                new Object[] {}));
        return this.getMessageByValidatorParams(errorValidators, csvs);
    }

    /**
     * 非同期処理結果を作成する.
     *
     * @param status 非同期処理のステータス
     * @param resultMessage 処理結果メッセージ
     * @param detailMessages 詳細メッセージ
     * @param param パラメータ
     * @return 非同期処理の結果
     */
    private AsyncProcessResult createAsyncProcessResult(AsyncProcessStatus status,
            String resultMessage, List<String> detailMessages, BudgetCaptureParam param) {
        List<AdditionalMessage> additionalMessages = new ArrayList<>();

        AsyncProcessResult result = new AsyncProcessResult();
        result.setStatus(status);
        result.setMessage(resultMessage);
        if (CollectionUtils.isNotEmpty(detailMessages)) {
            // サマリーメッセージ
            //摘要消息
            String summaryMessage = Objects.equals(status, AsyncProcessStatus.ERROR)
                    ? Message.getMessage("GL300E019")
                    : Message.getMessage("GL300I006");
            AdditionalMessage additionalMessage =
                    new AdditionalMessage(summaryMessage, detailMessages);
            additionalMessages.add(additionalMessage);
            result.setAdditionalMessages(additionalMessages);
        }
        if (status == AsyncProcessStatus.SUCCESS) {
            this.budgetCaptureNotification(param);
        }
        return result;
    }
}

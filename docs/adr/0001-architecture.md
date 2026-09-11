# ADR-0001: cloud-itonami-isic-6202 — SupportOps-LLM を封じ込めた知能ノードとするCRM連携カスタマーサービスハブ actor 設計

- Status: Accepted (2026-07-12)
- 関連: `cloud-itonami-isic-5820`(RevOps-LLM ⊣ SubscriptionGovernor、
  この actor を自身の sibling-actor roadmap で名指ししている直接の
  手本)、`cloud-itonami-isic-6209`(TicketRouter-LLM ⊣ TicketGovernor、
  形は似るが業務モデルが異なる sibling)、`cloud-itonami-isic-8220`
  (call centre、これも異なる業務モデル)、`cloud-itonami-isic-6920`
  (double-guard を dedicated boolean で行う設計の直接の教訓元)、
  `kotoba-lang/crm`(この build が再利用する技術commons)、
  langgraph-clj ADR-0001

## 課題

ISIC Rev.4 6202「Computer consultancy and computer facilities management
activities」は広いコードであり、単純な relabeling を避けるため、UNSD の
公式 explanatory note(class 6202)が含む "provision of on-site
management and operation of clients' computer systems and/or data
processing facilities, plus related support services" のうち **"related
support services" を、CRM/subscription 連携カスタマーサービスハブ SaaS
プラットフォーム事業**(HubSpot Service Hub/Salesforce Service Cloud
クラス)に narrow した。SupportOps-LLM に case status 確定・SLA
コミットメント・KB 公開を直接行わせると、未払い entitlement の付与・
ライフサイクル整合性違反・エンバーゴ情報の漏洩・無権限の返金コミットメント
のリスクがある。

`cloud-itonami-isic-5820` は自身の `docs/business-model.md` の
"Sibling-actor roadmap" 節で本 actor を名指ししている:
*"Customer-service hub (support cases, SLAs, knowledge base) — HubSpot
Service Hub/Salesforce Service Cloud equivalent. Note
`cloud-itonami-isic-6209` already covers IT-managed-services/helpdesk
ticket routing specifically; a CRM-integrated customer-service hub
would be a distinct, account/subscription-aware sibling, not a
duplicate of 6209's scope."* 本 build はその sibling である。

## 決定

SupportOps-LLM は proposal のみを返す助言者とし、独立した
ServiceGovernor がすべての case status 遷移・KB 公開・開示・紛争解決を
検閲する。**単一不変条件**: SupportOps-LLM は、ServiceGovernor が拒否
する status 遷移確定・KB 公開・開示・紛争解決を決して行わない。

domain-unique HARD チェック2つ(この fleet で新規): `sla-tier-gate`
(case status 遷移が account の有効な subscription/SLA tier より速い
応答時間コミットを行おうとしたら拒否 — `cloud-itonami-isic-5820`の
entitlement-scope-gate と同型だが対象は feature/seat ではなく応答時間
コミットメント)、`case-status-sequence-gate`(case ライフサイクルの
スキップ/逆行を拒否 — `kotoba-lang/crm`の`kotoba.crm.pipeline`を再利用、
-isic-5820の stage-sequence-gate と同型)。

`double-close-gate` は 6920/-isic-5820 の教訓(status-lifecycle バグ)を
踏まえ、`:status` 値ではなく専用 `:closed?` boolean で二重クローズを
防止する。

SOFT gate 3つ: `kb-embargo-gate`(この fleet で新規の check kind —
エンバーゴ済み KB 記事の公開提案は確信度に関わらず常に人間承認)、
`refund-commitment-gate`(返金/クレジットを示唆する case コミットメントは
常に人間承認)、`dispute-request`(無条件、どの phase でも auto-commit
しない)。

## 6209/8220 との差別化(collision チェック)

- `cloud-itonami-isic-6209`(IT managed-services/helpdesk ticket-routing、
  TicketRouter-LLM ⊣ TicketGovernor)は account/subscription エンティティを
  一切持たない — technician の access-tier/certification によるルーティング
  のみ。本 actor はその逆で、case は常に account の subscription tier に
  紐づき、SLA は tier の関数として governor される。schema・governor
  gate・想定オペレータ(MSP dispatch desk vs. SaaS ベンダー自身の
  customer-support hub)のいずれも重複しない。
- `cloud-itonami-isic-8220`(call centre)は agent 登録/dispatch と
  品質保証記録を扱う staffing/BPO 事業であり、そもそもソフトウェア製品
  ではない。本 actor は他社が自社の support desk を回すために買う/
  self-host する SaaS プラットフォームであり、staffing agency ではない。

## kotoba-lang/crm の再利用

`kotoba.crm.pipeline`(汎用 stage 遷移検証、`cloud-itonami-isic-5820`が
新規切り出しした技術commons)を case ライフサイクル(`:new → :in-progress
→ :resolved → :closed` + `:cancelled` exit)に再利用した — sales-pipeline
の stage 形状と直接同型であるという判断による、genuine な再利用(単なる
コピーではない)。`kotoba.crm.revrec`/`kotoba.crm.leadscore` は本 actor の
責務(revenue recognition/lead scoring)外なので採用しない。

## Consequences

- (+) `kotoba-lang/industry` registry 6202 スロットが実装へ昇格。
- (+) narrowing 判断を明記(UNSD explanatory note の "related support
  services" 語句を根拠として引用、恣意的な relabeling を回避)。
- (+) `sla-tier-gate`/`case-status-sequence-gate` はこの fleet の
  check-kind 語彙への genuine な追加、`kb-embargo-gate` も同様(埋め込み
  コンテンツのエンバーゴ判定という新しい check kind)。
- (+) `kotoba-lang/crm`の`kotoba.crm.pipeline` 再利用により、
  `cloud-itonami-isic-5820`が意図した「sibling actor が同じ pipeline
  ロジックを再導出しない」という設計方針が実証された(2件目の consumer)。
- (+) `MemStore` ‖ `DatomicStore` parity は
  `test/svcdesk/store_contract_test.cljk` で証明。
- (-) R0 は3 SLA/subscription tier・4-status 線形ライフサイクルのみ
  (ブランチ/並行 status は対象外)。
- (-) SLA governance は単一の応答時間コミットメント check のみ
  (多メトリック SLA・per-agent authority tier・自動 breach-credit 計算は
  対象外)。
- (-) KB governance は `:embargoed?` boolean のみ(バージョニング・
  エンバーゴ以外の承認ワークフロー・全文コンテンツモデレーションは
  対象外)。

## 代替案と不採用理由

| Option | Verdict | Reason |
|---|---|---|
| ISIC 6209(Other information technology and computer service activities) | ❌ | 既に IT-helpdesk ticket-routing で claimed(`cloud-itonami-isic-6209`、`:maturity :implemented`)。CRM/subscription 連携という異なる業務モデルを同一コードに重ねない |
| ISIC 8220(Activities of call centres) | ❌ | 既に call-centre staffing/BPO で claimed(`cloud-itonami-isic-8220`、`:maturity :blueprint`)。ソフトウェア製品ではなく人材派遣サービス業なので、そもそも業務モデルとして別物 |
| ISIC 6201(Computer programming activities) | ❌ | registry 上 `:maturity :spec` で未claimedだが、`kotoba-lang/crm`のREADMEが明記する通り marketing-automation actor(HubSpot Marketing Hub/Salesforce Marketing Cloud equivalent)の予約先 — `cloud-itonami-isic-5820`のroadmap上の別 sibling(この fleet で並行して別 agent がbuildしうる)であり、customer-service hub とは異なる業務モデル |
| ISIC 8211(Combined office administrative service activities) | ❌ | 公式定義は reception/財務計画/請求書処理/郵便業務等の一般オフィス事務代行であり、support-case/SLA/KB を持つカスタマーサービス SaaS とは業務モデルが一致しない |
| ISIC 6391(News agency activities)/8230(Organization of conventions and trade shows)/8292(Packaging activities)/8412(Regulation of health/education activities) | ❌ | 業務内容がカスタマーサービスハブと無関係(報道・展示会運営・梱包・行政規制) |
| ISIC 6202(Computer consultancy and computer facilities management activities) — 採用 | ✅ | UNSD explanatory note が明記する "on-site management and operation of clients' computer systems ... plus **related support services**" を、ホスト型・マルチテナントの customer-service-hub SaaS(clientの support desk を facility として運用する形態)として narrow できる。未claimedの `:spec` entry の中で、IT/software 領域かつ「サポートサービス」という語句を公式定義が明示的に含む唯一の候補 |
| CRM(5820)/marketing(6201想定)/customer-service を1つの actor にまとめる | ❌ | オーナー指示「business modelごとに設計」・fleet の one-business-model-per-actor 規律に反する |
| pipeline ロジックを actor 内に private に留める | ❌ | `kotoba-lang/crm`の既存設計方針(-isic-5820が明記)に反する。2件目の consumer として再利用することが sibling-actor roadmap の意図そのもの |
| double-close を `:status` 値だけで判定 | ❌ | ADR-2607071320 で確認済みの status-lifecycle バグと同じ罠(-isic-5820も同じ理由で回避) |

## References

- `cloud-itonami-isic-5820/docs/business-model.md`(この actor を
  sibling-actor roadmap で名指しした直接の根拠)
- `cloud-itonami-isic-5820/docs/adr/0001-architecture.md`(直接の手本)
- `cloud-itonami-isic-6209/docs/business-model.md`(差別化対象、直接比較)
- ADR-2607071351(`cloud-itonami-isic-6920`、double-guard 設計の教訓元)
- UNSD ISIC Rev.4 Classification Detail, code 6202
  (https://unstats.un.org/unsd/classifications/Econ/Structure/Detail/EN/27/6202)
- `kotoba-lang/crm`(この build が再利用した技術commons、README が本
  actorを2件目の`kotoba.crm.pipeline` consumerとして記載)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)

## Verification Notes

- `clojure -M:dev:test` — 33 tests, 110 assertions, 0 failures, 0 errors
  (`test/svcdesk/{facts,llm,phase,policy-contract,store-contract}_test.clj`)
- `clojure -M:lint`(clj-kondo, `--fail-level error`) — 0 errors, 0 warnings
- `clojure -M:dev:run` — 12-operation demo confirms all 6 HARD gates and
  3 SOFT/always-escalate gates fire as designed, plus MemStore/
  DatomicStore parity via the shared `Store` protocol.

# CRM-Integrated Customer-Service-Hub Actor Design

SupportOps-LLM を最下層ノードに封じ込め、ServiceGovernor(独立系統)が
SLA entitlement・case status 順序・エンバーゴ済み KB 公開・返金/クレジット
示唆を検閲する構図。HubSpot Service Hub/Salesforce Service Cloud クラスの
CRM連携カスタマーサービスハブ事業を ISIC Rev.4 6202(Computer consultancy
and computer facilities management activities)に narrow して実装した、
`cloud-itonami-isic-5820`(commercial CRM/subscription-commerce)の
sibling actor その2。`cloud-itonami-isic-6209`(TicketRouter-LLM ⊣
TicketGovernor)の写像だが、CRM/subscription 連携という異なる業務モデル。

## 1. なぜ actor 層が要るのか

サポートケースの進行/開示は LLM で加速できるが、**最終的な確定権限を
持たせるのは危険**:

| LLM が起こしうる失敗 | 帰結 |
|---|---|
| account の subscription tier を超える応答時間コミット | 未払い entitlement の付与 |
| case status をスキップ/逆行して確定 | ライフサイクル整合性の空洞化 |
| 既に closed 済みの case を再遷移 | 監査可能性の毀損 |
| エンバーゴ済み KB 記事を自動公開 | 未発表情報の外部漏洩 |
| 返金/クレジットを示唆するコミットメントを自動承認 | 財務上の無権限コミットメント |

## 2. OperationActor(`src/svcdesk/operation.cljc`)

```
intake → advise → govern → decide ─┬─ commit
                                   ├─ escalate ─▶ request-approval → commit|hold
                                   └─ hold
```

## 3. ServiceGovernor(`src/svcdesk/policy.cljc`)

優先順位(HARD は人間承認でも上書き不可):

1. rbac
2. **sla-tier-gate**(新規 check kind) — case status 遷移が account の
   有効な subscription/SLA tier より速い応答時間コミットを行おうとしたら
   拒否
3. **case-status-sequence-gate**(新規 check kind) — `kotoba.crm.pipeline`
   による case ライフサイクル遷移の正当性(スキップ/逆行不可)
4. **double-close-gate** — 専用 `:closed?` boolean で二重クローズを防止
   (ADR-2607071320 の status-lifecycle バグから学んだ設計)
5. source-provenance-gate
6. licensed-disclosure
7. 確信度フロア(SOFT)
8. **kb-embargo-gate**(SOFT) — エンバーゴ済み KB 記事の公開提案は常に
   人間承認へ
9. **refund-commitment-gate**(SOFT) — 返金/クレジットを示唆する
   コミットメントは常に人間承認へ
10. dispute-request(SOFT、無条件)

## 4. SSoT(`src/svcdesk/store.cljc`)

agents(name のみ)・accounts(subscription-tier/active?)・cases
(status/assigned-agent/committed-response-hours/closed?)・kb-articles
(title/status/embargoed?)・append-only ledger。

## 5. R0(`src/svcdesk/facts.cljc`)

出典クラス3種 + 3段階 SLA/subscription tier + 4-status 線形 case
ライフサイクル(+1 exit status)。

## 6. Phase 0→3(`src/svcdesk/phase.cljc`)

`default-phase` = 1(保守的)。`dispute/request` はどの phase の `:auto`
にも入らない。`kb/publish-article` は phase 3 の `:auto` に入るが、
kb-embargo-gate(SOFT)がエンバーゴ済み記事を phase に関わらず常に
escalate させる。

## 7. 技術的共通(`kotoba-lang/crm`)

`kotoba.crm.pipeline`(汎用 stage 遷移検証)を case ライフサイクルに
再利用。`kotoba.crm.revrec`/`kotoba.crm.leadscore` はこの actor の
責務外(revenue recognition/lead scoring を行わないため)なので使わない。

# physai-isic-8422 — 防衛調達・兵站の行政（ISIC 8422）の受入検査・倉庫ロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-8422`、ISIC Rev.5 8422 防衛の調達・兵站の行政）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 文書取扱い・検証ロボットが取引先の審査、調達要求の起案、兵站の調整を actor の下で行い、Defence Procurement Governor が独立に止める（一定額以上の予算確定や契約決定は人の承認が要る）。
ここで測るのは検証と兵站の物理 —— 納入品の受入検査と倉庫内の移動 —— で、それを `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:delivered-steel-lot-coupon-test` | material | 取引先が納入した S355 鋼材ロットから切り出した丸棒試験片（直径 10 mm、標点距離 50 mm）を受入検査で引張試験する。ロットの実際の降伏強さを振る | 0.2 % 耐力荷重 | 下限 27882 N（出典: EN 10025-2 S355 の最小降伏強さ 355 MPa × 試験片断面 78.54 mm²） |
| `:delivered-pallet-to-quarantine-bay` | transport | 倉庫 AMR が納入された 1 t のパレットを受入ドックから受入保留区画へ運ぶ（区画までの距離は倉庫の配置で決まる） | 1 区間の所要時間 | 75 s（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test-physai/defence/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する）。
この repo 自身の `.kotoba` test は kbb では走らない（fleet の JVM gate が走らせる）。この bot の test 数は physics の test だけを数える（2 test / 5 assertion）。

## 測って分かったこと・限界（成長の第一候補）

1. **受入引張試験**: 耐力荷重は降伏強さ 320 MPa のロットで 25312 N、340 MPa で 26878 N、355 MPa で 28056 N、400 MPa で 31593 N。
   solver の読みは公称 σy·A より約 175 N（0.6 %）高い（加工硬化 1 GPa × 0.2 % 分）。そのため合否の境界は **352.8 MPa** に出る ——
   規格どおり 355 MPa で切るなら、この 0.6 % の読み過ぎを判定側で差し引く必要がある（現状の判定は 2.2 MPa だけ甘い）。
2. **パレット搬送**: 所要時間は距離にほぼ比例（30 m で 23.75 s、80 m で 57.08 s、150 m で 103.75 s）。速度上限 1.5 m/s と加速度上限 0.3 m/s² が効き、
   1 t 積みでも駆動力 700 N は制約にならない。75 s に収まる距離は **106.9 m**。停止距離 1.875 m、転倒余裕 0.94。
3. **estimate のままの値**: ドックの 75 s（荷下ろし計画の実値）、AMR の駆動力・加減速・転がり抵抗（機体の仕様書）、鋼材の加工硬化係数 1 GPa（ミルシートや試験の実測）。
   出典付き: S355 の最小降伏 355 MPa は EN 10025-2（板厚 16 mm 以下）。発注仕様が別規格（JIS G 3106 SM490 等）ならその値に置き換える。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-8422 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-8422 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。

# Forbidden Chimera

Sew a phantom and a creeper together — then let them come looking for you.

This mod adds six "phantom × creeper/enderman" chimeras, plus two **variants** of one of them and a **boss**. All of them replace the vanilla phantom during **insomnia nights**: the longer you go without sleeping, the more of them show up.

## The creatures

| Creature | What it does |
|---|---|
| **Phantom Rider Creeper** | A phantom with a real creeper riding on its back. It dives straight at you and detonates the creeper on contact — and does not survive the blast itself. If you shoot the creeper off first, it never gets a new one. |
| **Creeper Thrower Phantom** | A phantom with a creeper hanging under its belly. It climbs above you, drops the creeper on your head, then **immediately grabs a new one** and waits for the cooldown. The dropped creeper is a normal creeper: it chases you and explodes on its own. |
| **Creeper Phantom** | An original chimera: creeper head and torso, phantom wings and tail. It circles to a spot about **12 blocks diagonally above** you, fires a short **salvo of explosive creeper skulls** from up there, and only then dives in — so it never charges into its own blast. The skulls always come from a visible, dodgeable angle. |
| **Lightning Creeper Phantom** | The charged sibling: identical AI, but the torso and head carry the **vanilla charged-creeper energy swirl** on top of the texture (wings and tail stay clean). It fires **lightning creeper skulls** with a bigger blast radius and barely takes damage from its own explosions. |
| **Nuclear Creeper Phantom** | The red, one-use missile. It circles you only briefly, retreats to a hover about **10 blocks up and 8 blocks to the side**, and its **wing flap keeps accelerating** — that is the countdown. Then it dives and self-destructs with a **10-block blast that also sets the ground and everything caught in it on fire**. Only 8 hearts, so shoot it down while it hovers. |
| **Enderman Phantom** | Enderman body, phantom wings. It fires **no skulls** — its dive is a body slam — but it **teleports away from projectiles**: bows barely land a hit, and the dodge itself costs it nothing. Occasional idle teleports too. Melee and fall damage work normally. |

### The two variants of the Creeper Phantom

Same entity, different look — the name on screen is still "Creeper Phantom / Lightning Creeper Phantom":

| Variant | Look | Difference |
|---|---|---|
| **Spear** (Creeper / Lightning Creeper Phantom, 10% each) | **One spear under each wing**, lying flat | The spears come in **seven materials** (wood/stone/copper/iron/gold/diamond/netherite), one random pair per individual. The dive hit is resolved with the **spear's own kinetic weapon rules**: the better the spear and the faster the dive, the harder it hits. |
| **Knight** (Creeper Phantom only, 10%) | A baby zombie with a **mace** riding on its back | It keeps diving and firing as usual, and now and then (about once every half a minute) it climbs above you and **drops straight down**, letting the zombie's mace settle the hit. The zombie may roll a full matching armour set with enchantments. |

The two variants are mutually exclusive. Kill the zombie and the mount goes back to being a plain Creeper Phantom.

### And a boss

**Boss · Lightning Creeper Phantom Knight**: it **never spawns naturally** — summon it with the **Forbidden Plank**. It carries a full head arsenal: three wither skulls per wing, a warden head between two ender dragon heads, firework rockets on its back, and a trident-wielding baby zombie riding on top (**50 HP, takes only 10% of explosion damage**, so it does not die with its mount). It:

- constantly lobs **lightning creeper skulls**, and switches to a **wither skull salvo** (three heads per side, alternating) for its burst;
- breathes **dragon's breath** through the dragon heads;
- **lights its rockets** and rockets straight at you, letting the baby zombie stab with its trident (it **pauses briefly after a side-arm shot** first, so it never charges into its own blast);
- answers with a **warden sonic boom** (10 damage + heavy knockback) after the pass;
- sometimes blankets the area in **darkness**;
- and takes **10% of all explosion damage** — TNT is a tickle to it.

**How to summon**: craft one Forbidden Plank with a shapeless recipe of **echo shard + wither skeleton skull + dragon head + phantom membrane + rotten flesh + gunpowder**, then **hold right click for 3 seconds** on the ground. Each plank is consumed.

## How to meet them

- **Natural spawning**: exactly like the vanilla phantom — dark, open sky, above sea level, difficulty check passed, and **about 3 in-game days without sleeping**. They arrive in groups, just like vanilla — and **real vanilla phantoms still show up among them** (`vanillaSpawnWeight`, default 4).
- **Spawn eggs**: all six creatures **and the boss** have spawn eggs in the creative menu (the two variants share their base creature's egg — raise the chances in the config instead).
- **Commands**: `/summon` works too; see the development docs for the NBT keys that force a specific variant.

## Drops

- Phantom family: **phantom membrane** and **gunpowder** (the lightning variant drops more).
- Nuclear Creeper Phantom drops the most: **2–4 membrane, 2–4 gunpowder**.
- Enderman Phantom: **1–2 membrane and 1 ender pearl**.
- Dropped/carried creepers drop like vanilla creepers: **gunpowder**.
- The knight variant's baby zombie drops like a vanilla baby zombie, including its mace.
- **Boss**: **4–6 membrane, 3–5 gunpowder, 2–4 ancient debris**; its rider drops the trident.

## Configuration

First launch writes `config/forbidden_chimera.json`; it is plain JSON, open it in any editor. Highlights:

| Key | Meaning |
|---|---|
| `enable*` / `*SpawnWeight` | Toggle each creature / its natural-spawn weight (0 = never spawns naturally). `vanillaSpawnWeight` (4) is the **plain vanilla phantom's** share — set it to 0 and the chimeras fully replace the vanilla phantom |
| `attackCreativePlayers` | Off by default: the chimeras ignore creative / invulnerable players, exactly like vanilla. Turn it on only if you want to test their aggression from creative mode. Plain vanilla phantoms always ignore them |
| `spearVariantChance` / `knightVariantChance` | Variant chances (both 0.10 by default) |
| `riderMaxHealth` / `riderExplosionDamageFactor` | Health of the baby zombie on a mount's back (50, vanilla zombies have 20) / explosion damage taken by a **passenger of the blast source** (0.1 = 90% off) |
| `diverSalvoCount` / `diverSalvoIntervalTicks` | Skull salvo fired from the staging point **before** the dive / interval between them (3 / 12). The real cadence is the slower of this and `diverFireCooldownTicks`; set the count to 0 for the old "fire only while diving" behaviour |
| `nuclearExplosionFireTicks` / `nuclearExplosionFireChance` | Nuclear blast fire: how long caught entities burn (100 ticks = 5s) / chance per ground block to leave fire (0.25, 0 = no fire) |
| `bossSideArmCooldownTicks` | How often the boss fires a lightning creeper skull while not diving (60) |
| `bossSideArmDiveGraceTicks` | Minimum delay after a side-arm shot before it may commit to the rocket dive (30) |
| `bossSalvoCount` / `bossSalvoIntervalTicks` | Wither skulls per burst / the interval between them (5 / 3) |
| `bossOrbitTicks` | How long it cruises before committing to a special attack (30) |
| `bossExplosionDamageFactor` | Fraction of explosion damage the boss takes (0.1) |

Every other knob (hover heights, speeds, cooldowns, blast radii, flap speed) lives in the same file. Changes apply to **newly spawned** creatures only.

## Installation

1. Install Minecraft **26.3** (Fabric or NeoForge).
2. **GeckoLib is required** (it drives the models and animations); Fabric also needs **Fabric API**.
3. Drop the jar into `mods`. No datapacks needed.

## Feedback

- Homepage / source: https://github.com/Huziyang520/Forbidden-Chimera
- Issues: https://github.com/Huziyang520/Forbidden-Chimera/issues
- Backup issue tracker: https://issue.mengcai.online/
- Author: Huziyang520

## License

MIT.

---

# 禁忌嵌合体 · Forbidden Chimera（中文）


## 生物一览

| 生物 | 它会做什么 |
|---|---|
| **幻翼骑士苦力怕** | 一只背上真的骑着普通苦力怕的幻翼。它发现你之后会直接朝你俯冲，贴脸时引爆背上的苦力怕，自己也不活。辛苦养的苦力怕被打死了也不会再补——它就这么飞着了。 |
| **苦力怕投手幻翼** | 肚子底下吊着一只普通苦力怕的幻翼。它爬到你的头顶上方，把吊着的苦力怕砸下来，接着**立刻再抓一只**继续吊着，等冷却结束再砸第二次。被丢下来的苦力怕是正常苦力怕，会追你、会自己爆。 |
| **苦力怕幻翼** | 原创嵌合体：苦力怕的头与躯干、幻翼的翅膀与尾巴。它先绕到你**斜上方约 12 格**处就位，在那儿**先连打几轮爆炸苦力怕头颅**，打完了才俯冲进场——所以它不会冲进自己刚打出去的爆炸里。头颅都是从远处斜着飞来的，看得见也躲得开。 |
| **闪电苦力怕幻翼** | 上面那只的带电版本：AI 完全一样，只是躯干与头**在原贴图之上叠加**原版闪电苦力怕的电流特效（翅膀和尾巴干净）。它发射**闪电苦力怕头颅**（尾迹电火花），爆炸范围更大，而且几乎不会被自己的爆炸伤到。 |
| **核弹苦力怕幻翼** | 红色的自爆型。它只在你周围**短促地盘旋一小会儿**，再退到你**斜上方约 10 格**悬停，悬停时**振翅越来越快**——那就是它在倒计时。悬停结束就朝你俯冲自爆，**爆炸半径 10 格，并且会把炸到的地面和实体都点着**。好消息是它只有 8 颗心，悬停时完全可以打下来。 |
| **末影人幻翼** | 末影人身体配幻翼翅膀。它**不发射头颅**，俯冲就是撞你；但它**会瞬移躲开弹射物**——弓箭基本射不中，而且躲开那一下完全不掉血。平时也会偶尔瞬移一下。近战和摔落照常吃伤害。 |

### 苦力怕幻翼的两个变体

同一个生物，外观不一样，游戏里**名字还是"苦力怕幻翼/闪电苦力怕幻翼"**：

| 变体 | 长什么样 | 有什么不同 |
|---|---|---|
| **持矛**（苦力怕幻翼 / 闪电苦力怕幻翼，各 10% 概率） | 翅膀下吊着一把矛 | 矛有**七种材质**（木/石/铜/铁/金/钻石/下界合金）随机一种。俯冲撞到你时**伤害改用这把矛的公式**：矛越好、俯冲越快，打得越疼。 |
| **骑士**（只有苦力怕幻翼，10% 概率） | 背上骑着一只小僵尸，手里拿着**重锤** | 平时照旧俯冲开火；偶尔（平均约半分钟一次）会爬到你头上再**笔直砸下来**，由小僵尸的重锤结算伤害——那一下比普通挥锤重得多。小僵尸有概率穿一整套护甲、还可能带附魔，而且**有 50 点血、几乎炸不死自己**（坐骑自爆时它只吃 10% 爆炸伤害）。 |

两个变体不会同时出现。把小僵尸打死，坐骑就变回普通苦力怕幻翼。

### 还有一位首领

**首领 · 闪电苦力怕幻翼骑士**：它**不会自然生成**，只能用**禁忌板材**召出来。长着三颗凋灵头、一只坚守者头和两只末影龙首，背上是烟花火箭，背上还骑着一只拿三叉戟的小僵尸（**50 点血、只吃 10% 爆炸伤害**，不会跟着坐骑一起被炸死）。它会：

- **平时**持续朝你发射**闪电苦力怕头颅**，**连发**时才左右两侧轮流打出一串**凋灵之手**（每侧三颗头依次来）；
- 咬合着龙首**吐龙息**；
- 突然**点燃烟花火箭加速**扑过来，让小僵尸用三叉戟捅你（**发完侧臂头颅会先隔一小会儿**再扑，免得它扎进自己刚打出的爆炸里）；
- 掠过后回头给你一发**坚守者音波**（10 点伤害 + 强击退）；
- 时不时用**黑暗**笼罩周围；
- **只怕十分之一的爆炸伤害**——TNT 对它几乎是挠痒。

**怎么召**：用 6 样东西**无序合成**一块禁忌板材 —— **回响碎片 + 凋零骷髅头 + 龙首 + 幻翼膜 + 腐肉 + 火药**。拿在手上**对着地面按住右键 3 秒**，松开时它会出现在你瞄准的位置。板材用掉一块少一块。

两种被丢下/被驮着的苦力怕都是**真正的原版苦力怕**：可以单独打死、会掉火药；主人死掉后它们会像普通苦力怕一样继续活动。骑士变体背上的小僵尸也一样——**可以单独打死**，坐骑死掉时它会掉下来变成普通僵尸继续追你。

## 怎么遇到它们

- **自然生成**：和你熟悉的原版幻翼完全同一套条件——天黑、露天、玩家在海平面以上、难度判定通过，并且**连续约 3 个游戏日没有睡觉**。满足后你会看到它们成群出现（和原版幻翼一样 1~多只），而且**里面仍然会有原版幻翼**（`vanillaSpawnWeight`，默认 4）。
- **刷怪蛋**：六种生物都有对应的刷怪蛋，创造模式刷怪蛋分类里可以直接拿（两个变体没有独立蛋，用配置把概率调高来观察）。
- **首领的蛋**：首领**不会自然生成**，但它也有自己的刷怪蛋，同样在创造模式的刷怪蛋分类里（生存里只能靠禁忌板材召唤）。
- **指令**：`/summon` 也能生成，具体写法（含"生成无 AI 的展示用个体"）见开发文档目录里的《本项目-生物列表与测试指令》。

## 掉落

- 幻翼类掉落：**幻翼膜**、**火药**（闪电苦力怕幻翼掉得更多一些）。
- 核弹苦力怕幻翼掉得最多：**幻翼膜 2~4、火药 2~4**。
- 末影人幻翼：**幻翼膜 1~2、末影珍珠 ×1**。
- 被丢下/被驮着的苦力怕按原版苦力怕掉落：**火药**。
- 骑士变体背上的小僵尸按原版小僵尸掉落；它手里的重锤也会掉。
- 首领掉落：**幻翼膜 4~6、火药 3~5、远古残骸 2~4**；它背上的小僵尸另外掉三叉戟。

## 配置

首次启动会在 `config/forbidden_chimera.json` 生成配置，用记事本就能改。常用项：

| 配置项 | 作用 |
|---|---|
| `enablePhantomRiderCreeper` / `enableCreeperThrowerPhantom` / `enableCreeperPhantom` / `enableLightningCreeperPhantom` / `enableNuclearCreeperPhantom` / `enableEndermanPhantom` | 分别开关六种生物 |
| `riderSpawnWeight` / `throwerSpawnWeight` / `vanillaSpawnWeight` / `diverSpawnWeight` / `lightningDiverSpawnWeight` / `nuclearSpawnWeight` / `endermanSpawnWeight` | 自然生成权重（默认 3/3/**4**/4/1/1/3），改成 0 就不再自然生成。`vanillaSpawnWeight` 是**纯原版幻翼**的权重——它保证自然生成里仍有原版幻翼，改成 0 就等于让嵌合体完全顶掉原版幻翼 |
| `attackCreativePlayers` | 默认**关闭**：创造模式 / 无敌玩家不会被它们盯上，与原版一致（老配置会自动迁移成 `false`）。想用创造模式测试它们的攻击性就改成 `true`。**纯原版幻翼永远不吃这个键** |
| `chimeraSpawnerUsesPhantomGameRule` | 默认开启：`/gamerule spawn_phantoms false` 会连同本模组一起关掉。想单独保留它们就改成 `false` |
| `riderChargeSpeed` / `riderDetonateDistanceSqr` | 骑士（mob1）的俯冲速度、引爆距离 |
| `throwerHoverHeight` / `throwerThrowCooldownTicks` / `throwerThrowSpeed` | 投手悬停高度、投掷间隔、投出速度 |
| `diverClimbHeight` / `diverDiveSpeed` / `diverFireCooldownTicks` | 幻翼的就位高度、俯冲速度、开火间隔 |
| `diverApproachOffset` / `diverFireMinDistance` / `diverPassByTicks` | 幻翼在玩家斜上方多远就位（默认 12 格）；俯冲到近于此距离就停火（默认 7 格）；一次俯冲结束后最多再飞多久（默认 40 tick）。想让它们贴脸也开火就把 `diverFireMinDistance` 改成 0 |
| `diverSalvoCount` / `diverSalvoIntervalTicks` | 俯冲**之前**先在就位点连打几轮头颅 / 每轮之间的间隔（默认 3 轮、12 tick）。真实节奏取它和 `diverFireCooldownTicks` 里较慢的那个；把 `diverSalvoCount` 改成 0 就恢复成"俯冲时才开火"的旧行为 |
| `skullExplosionRadius` / `skullOwnerDamageFactor` | 普通苦力怕头颅的爆炸威力、对发射者自身的伤害比例 |
| `lightningSkullExplosionRadius` / `lightningSkullOwnerDamageFactor` | 闪电苦力怕头颅的爆炸威力、对发射者自身的伤害比例（默认只吃 2%） |
| `nuclearHoverHeight` / `nuclearHoverOffset` / `nuclearCircleTicks` / `nuclearHoverTicks` / `nuclearChargeSpeed` / `nuclearExplosionRadius` / `nuclearFlapSpeedMax` | 核弹苦力怕幻翼：悬停高度 / 斜上方水平外移 / 盘旋时长（默认 40 tick）/ 悬停时长 / 俯冲速度 / 爆炸半径（默认 **10 格**）/ 悬停末端的振翅倍速 |
| `nuclearExplosionFireTicks` / `nuclearExplosionFireChance` | 核弹爆炸的火焰：被点燃的实体烧多久（默认 100 tick = 5 秒）/ 爆炸范围内每个地表方块留火的概率（默认 0.25，改 0 就不留火） |
| `endermanIdleTeleportChance` / `endermanSpawnWeight` | 末影人幻翼：平时每个 tick 的瞬移概率（默认 0.002）/ 生成权重 |
| `enableSpearVariant` / `spearVariantChance` / `spearDiveDamageScale` | 持矛变体：是否启用 / 出现概率（默认 10%）/ 俯冲伤害的额外倍率（默认 1.0） |
| `enableKnightVariant` / `knightVariantChance` | 骑士变体：是否启用 / 出现概率（默认 10%） |
| `knightArmorChance` / `knightWeaponEnchantChance` / `knightArmorEnchantChance` | 骑手小僵尸的护甲概率（0.15）、重锤附魔概率（0.25）、护甲附魔概率（0.5） |
| `riderMaxHealth` / `riderExplosionDamageFactor` | 坐骑背上那只小僵尸的最大生命值（默认 50，原版僵尸是 20）/ **骑在爆炸源身上的乘客**吃爆炸伤害的倍率（默认 0.1 = 减免 90%）。`riderExplosionDamageFactor` 保护的是**乘客**，坐骑本体自己吃多少由各自的减免项决定 |
| `knightClimbHeight` / `knightClimbSpeed` / `knightChargeSpeed` / `knightChargeCooldownTicks` / `knightChargeChance` / `knightSmashMinFallDistance` / `knightRespawnRider` | 骑士冲撞：爬升高度 / 爬升速度 / 俯冲速度 / 冷却 / 每周期触发概率 / 触发重锤下落加成所需的最小下降 / 骑手死后是否补充（默认否） |
| `bossExplosionDamageFactor` | 首领吃多少爆炸伤害（默认 0.1 = 只吃 10%，**原版 TNT 也算**） |
| `bossOrbitHeight` / `bossOrbitRadius` / `bossOrbitSpeed` / `bossOrbitTicks` | 首领盘旋：高度 / 半径 / 速度 / 时长 |
| `bossSideArmCooldownTicks` | 首领**平时**（不俯冲时）多久发一颗**闪电苦力怕头颅**（默认 60 tick） |
| `bossSideArmDiveGraceTicks` | 首领发完侧臂头颅后，至少隔这么久才允许转入火箭俯冲（默认 30 tick），免得它一头扎进自己刚打出的爆炸里 |
| `bossSalvoCount` / `bossSalvoIntervalTicks` | **连发**（齐射）一轮几颗**凋灵之手** / 每颗间隔（默认 5 颗、3 tick） |
| `bossDragonBreathCount` / `bossDragonBreathIntervalTicks` / `bossDragonBreathFirstTick` | 龙息：几发 / 间隔 / 张口后第几 tick 出第一发（默认 7 = 0.35 秒，与动画张口帧对齐） |
| `bossSonicBoomTick` | 音波在动画里的第几 tick 触发（默认 20） |
| `bossDarknessRadius` / `bossDarknessDurationTicks` | 黑暗的作用半径 / 持续时间 |
| `bossRocketBoostTicks` / `bossRocketBoostMultiplier` / `bossDiveSpeed` / `bossMaxDiveTicks` | 烟花加速：持续 tick / 提速倍率 / 俯冲速度 / 俯冲最长 tick |

改完保存，**新生成**的生物就会用新数值（已经存在的那只不会变）。

## 安装

1. 安装对应加载器的 Minecraft（本模组双端支持 **Fabric** 与 **NeoForge**）。
2. **必须**安装前置 **GeckoLib**（苦力怕幻翼与闪电苦力怕幻翼的模型动画由它驱动）；Fabric 端还需要 **Fabric API**。
3. 把本模组的 jar 丢进 `mods` 文件夹。
4. 启动游戏即可，无需额外数据包。

## 反馈

- 主页（源码）：https://github.com/Huziyang520/Forbidden-Chimera
- 问题反馈：https://github.com/Huziyang520/Forbidden-Chimera/issues
- 备用问题反馈：https://issue.mengcai.online/
- 作者：Huziyang520

## 许可

MIT。

"""Build the 1000-session dataset for the Japanese learning app.

Sessions 1-84: the starter course (curriculum.txt).
Sessions 85-1000: JLPT vocabulary, N5 -> N4 -> N3 -> N2 (open-anki-jlpt-decks, MIT),
with a review session every 7th session. Output: sessions.tsv
(session, topic, japanese, reading, romaji, meaning)."""
import csv
import random
import re

SESSIONS = 1000
PER = 5

# ---------- kana -> Hepburn romaji (with long-vowel macrons) ----------
H2R = {
    'あ': 'a', 'い': 'i', 'う': 'u', 'え': 'e', 'お': 'o', 'か': 'ka', 'き': 'ki', 'く': 'ku', 'け': 'ke', 'こ': 'ko',
    'さ': 'sa', 'し': 'shi', 'す': 'su', 'せ': 'se', 'そ': 'so', 'た': 'ta', 'ち': 'chi', 'つ': 'tsu', 'て': 'te', 'と': 'to',
    'な': 'na', 'に': 'ni', 'ぬ': 'nu', 'ね': 'ne', 'の': 'no', 'は': 'ha', 'ひ': 'hi', 'ふ': 'fu', 'へ': 'he', 'ほ': 'ho',
    'ま': 'ma', 'み': 'mi', 'む': 'mu', 'め': 'me', 'も': 'mo', 'や': 'ya', 'ゆ': 'yu', 'よ': 'yo',
    'ら': 'ra', 'り': 'ri', 'る': 'ru', 'れ': 're', 'ろ': 'ro', 'わ': 'wa', 'を': 'o', 'ん': 'n',
    'が': 'ga', 'ぎ': 'gi', 'ぐ': 'gu', 'げ': 'ge', 'ご': 'go', 'ざ': 'za', 'じ': 'ji', 'ず': 'zu', 'ぜ': 'ze', 'ぞ': 'zo',
    'だ': 'da', 'ぢ': 'ji', 'づ': 'zu', 'で': 'de', 'ど': 'do', 'ば': 'ba', 'び': 'bi', 'ぶ': 'bu', 'べ': 'be', 'ぼ': 'bo',
    'ぱ': 'pa', 'ぴ': 'pi', 'ぷ': 'pu', 'ぺ': 'pe', 'ぽ': 'po', 'ゔ': 'vu',
    'ぁ': 'a', 'ぃ': 'i', 'ぅ': 'u', 'ぇ': 'e', 'ぉ': 'o',
}
YOON = {'ゃ': 'a', 'ゅ': 'u', 'ょ': 'o'}
MACRON = {'a': 'ā', 'i': 'ī', 'u': 'ū', 'e': 'ē', 'o': 'ō'}


def kata_to_hira(s):
    return ''.join(chr(ord(c) - 0x60) if 'ァ' <= c <= 'ヶ' else c for c in s)


def to_romaji(reading, is_verb=False):
    s = kata_to_hira(reading)
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        nxt = s[i + 1] if i + 1 < len(s) else ''
        if c == 'っ':
            # double the next consonant
            nx = s[i + 1] if i + 1 < len(s) else ''
            r = H2R.get(nx, '')
            if r.startswith('ch'):
                out.append('t')
            elif r:
                out.append(r[0])
            i += 1
            continue
        if c == 'ー':
            if out and out[-1] and out[-1][-1] in MACRON:
                out[-1] = out[-1][:-1] + MACRON[out[-1][-1]]
            i += 1
            continue
        if c in H2R and nxt in YOON:
            base = H2R[c]
            if base in ('shi', 'chi', 'ji'):
                syl = base[:-1] + YOON[nxt]
            else:
                syl = base[:-1] + 'y' + YOON[nxt]
            out.append(syl)
            i += 2
            continue
        if c == 'ん':
            out.append("n'" if nxt and (nxt in 'あいうえおやゆよ') else 'n')
            i += 1
            continue
        if c in H2R:
            syl = H2R[c]
            # long vowels: o+u -> ō, u+u -> ū, o+o -> ō (not at a verb's last mora)
            if out and out[-1] and syl in ('u', 'o') and i == len(s) - 1 and is_verb:
                out.append(syl)
            elif out and out[-1] and out[-1][-1] == 'o' and syl in ('u', 'o'):
                out[-1] = out[-1][:-1] + 'ō'
            elif out and out[-1] and out[-1][-1] == 'u' and syl == 'u' and not out[-1].endswith("n'"):
                out[-1] = out[-1][:-1] + 'ū'
            else:
                out.append(syl)
            i += 1
            continue
        out.append({'～': '~', '〜': '~', '；': ';', '、': ', ', '（': '(', '）': ')'}.get(c, c))  # punctuation, spaces
        i += 1
    return ''.join(out)


def short_meaning(m, limit=70):
    m = re.sub(r'\s+', ' ', m.strip())
    if len(m) <= limit:
        return m
    parts = m.split(', ')
    out = parts[0]
    for p in parts[1:]:
        if len(out) + 2 + len(p) > limit:
            break
        out += ', ' + p
    return out[:limit]


# ---------- starter course ----------
course = []  # (session, topic, jp, reading, romaji, meaning)
cur = None
for line in open('/home/claude/jpdata/curriculum.txt', encoding='utf-8'):
    line = line.rstrip('\n')
    if not line:
        continue
    if line.startswith('#'):
        n, topic = line[1:].split('|', 1)
        cur = (int(n), topic)
        continue
    jp, rom, mean = line.split('|')
    course.append((cur[0], cur[1], jp, '', rom, mean))
assert max(r[0] for r in course) == 84

seen = set()
for r in course:
    seen.add(kata_to_hira(r[2]).replace(' ', ''))

# ---------- JLPT vocabulary ----------
vocab = []
for level in ('n5', 'n4', 'n3', 'n2', 'n1'):
    rows = list(csv.DictReader(open(f'/home/claude/jpdata/raw/{level}.csv', encoding='utf-8')))
    rng = random.Random(f'jlpt-{level}')
    genki = [r for r in rows if 'Genki' in r['tags']]
    other = [r for r in rows if 'Genki' not in r['tags']]
    rng.shuffle(genki)
    rng.shuffle(other)
    for r in genki + other:
        expr, reading, meaning = r['expression'].strip(), r['reading'].strip(), r['meaning'].strip()
        if not expr or not meaning:
            continue
        reading = reading or expr
        has_kanji = lambda t: any('\u4e00' <= ch <= '\u9fff' or ch == '々' for ch in t)
        if has_kanji(reading) and not has_kanji(expr):
            expr, reading = reading, expr
        if has_kanji(reading):
            continue
        meaning = meaning.strip(' -')
        key = kata_to_hira(reading).replace(' ', '')
        key2 = expr
        if key in seen or key2 in seen:
            continue
        seen.add(key)
        seen.add(key2)
        is_verb = meaning.lower().startswith('to ')
        vocab.append((level.upper(), expr, reading if reading != expr else '', to_romaji(reading, is_verb), short_meaning(meaning)))

rows = list(course)
vi = 0
by_session = {}
for r in course:
    by_session.setdefault(r[0], []).append(r)
rng = random.Random('reviews')
for s in range(85, SESSIONS + 1):
    if s % 7 == 0:
        pool = [it for k in range(s - 6, s) for it in by_session.get(k, [])]
        picks = rng.sample(pool, PER)
        items = [(s, 'Review', p[2], p[3], p[4], p[5]) for p in picks]
    else:
        items = []
        lvl = vocab[vi][0]
        for _ in range(PER):
            level, expr, reading, rom, mean = vocab[vi]
            vi += 1
            items.append((s, f'JLPT {level} vocabulary', expr, reading, rom, mean))
    by_session[s] = items
    rows.extend(items)

with open('/home/claude/jpdata/sessions.tsv', 'w', encoding='utf-8') as f:
    for r in rows:
        f.write('\t'.join(str(x).replace('\t', ' ') for x in r) + '\n')

levels = {}
for r in rows:
    levels[r[1]] = levels.get(r[1], 0) + 1
print('rows', len(rows), 'sessions', len({r[0] for r in rows}), 'vocab used', vi, 'of', len(vocab))
print({k: v for k, v in levels.items() if k.startswith('JLPT') or k == 'Review'})
last_level = vocab[vi - 1][0]
print('last level reached:', last_level)

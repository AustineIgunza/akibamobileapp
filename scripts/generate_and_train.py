"""
Generate synthetic KES transaction data and train a Naive Bayes categorizer.
Outputs model.json for the Akiba Android app.
"""
import json, random, math, os
from collections import Counter
from datetime import datetime, timedelta

random.seed(42)

# ── Realistic KES transaction templates per category ──
TEMPLATES = {
    "Food & dining": {
        "amounts": (50, 3000),  # KES range
        "common_amounts": [100, 150, 200, 250, 300, 400, 500, 800, 1000, 1500, 2000],
        "descriptions": [
            "lunch", "breakfast", "dinner", "supper", "snack",
            "java house", "kfc", "chicken inn", "pizza inn", "subway",
            "mama mboga", "kibanda", "hotel lunch", "chapati",
            "mandazi", "nyama choma", "ugali", "fish", "pilau",
            "groceries", "supermarket", "naivas", "carrefour", "quickmart",
            "tuskys", "milk", "bread", "eggs", "sugar", "cooking oil",
            "vegetables", "fruits", "meat", "samosa", "juice",
            "coffee", "tea", "soda", "water", "food delivery",
            "uber eats", "glovo", "jumia food", "restaurant",
            "takeaway", "packed lunch", "office lunch", "chips",
            "smocha", "mutura", "githeri", "matoke", "sukuma wiki",
        ],
        "weight": 0.22,
    },
    "Transport": {
        "amounts": (30, 5000),
        "common_amounts": [30, 50, 70, 100, 150, 200, 300, 500, 1000, 2000],
        "descriptions": [
            "matatu", "bus", "uber", "bolt", "taxi", "boda boda",
            "pikipiki", "fare", "bus fare", "matatu fare", "stage",
            "commute", "fuel", "petrol", "diesel", "parking",
            "toll", "mpesa fare", "train", "sgr", "shuttle",
            "airport transfer", "travel", "trip", "safari",
            "car wash", "mechanic", "tyre", "puncture",
            "oil change", "service", "insurance", "ntsa",
            "ride home", "ride to work", "town trip", "cbd",
            "thika road", "mombasa road", "ngong road",
            "westlands", "kilimani", "karen", "eastlands",
        ],
        "weight": 0.18,
    },
    "Housing & rent": {
        "amounts": (5000, 80000),
        "common_amounts": [8000, 10000, 12000, 15000, 18000, 20000, 25000, 30000, 35000, 50000],
        "descriptions": [
            "rent", "house rent", "monthly rent", "landlord",
            "caretaker", "deposit", "agency fee", "house deposit",
            "apartment", "bedsitter", "single room", "one bedroom",
            "two bedroom", "studio", "flat", "estate",
            "house repair", "plumber", "painter", "renovation",
            "furniture", "curtains", "carpet", "mattress",
            "cleaning", "house help", "maid", "gardener",
        ],
        "weight": 0.08,
    },
    "Utilities": {
        "amounts": (100, 10000),
        "common_amounts": [200, 300, 500, 700, 1000, 1500, 2000, 3000, 5000],
        "descriptions": [
            "electricity", "kplc", "token", "power", "stima",
            "water bill", "nairobi water", "water",
            "wifi", "internet", "safaricom", "airtel", "telkom",
            "data bundle", "bundles", "airtime", "mpesa",
            "dstv", "gotv", "showmax", "netflix", "subscription",
            "gas", "cooking gas", "cylinder", "trash", "garbage",
            "sewage", "solar", "generator",
        ],
        "weight": 0.10,
    },
    "Shopping": {
        "amounts": (200, 30000),
        "common_amounts": [500, 1000, 1500, 2000, 3000, 5000, 8000, 10000, 15000],
        "descriptions": [
            "clothes", "shoes", "shirt", "trouser", "dress",
            "jacket", "jeans", "sneakers", "heels", "sandals",
            "phone", "charger", "earphones", "headphones", "case",
            "laptop", "tablet", "electronics", "tv", "speaker",
            "bag", "backpack", "handbag", "watch", "jewelry",
            "perfume", "cologne", "gift", "birthday gift",
            "household items", "utensils", "plates", "cups",
            "blanket", "bedsheets", "towel", "detergent", "soap",
            "online shopping", "jumia", "kilimall", "jiji",
        ],
        "weight": 0.10,
    },
    "Entertainment": {
        "amounts": (100, 10000),
        "common_amounts": [200, 300, 500, 1000, 1500, 2000, 3000, 5000],
        "descriptions": [
            "movie", "cinema", "imax", "concert", "show",
            "club", "bar", "drinks", "beer", "wine", "spirits",
            "outing", "hangout", "party", "event", "festival",
            "game", "bowling", "karting", "paintball", "arcade",
            "spotify", "apple music", "youtube premium",
            "betting", "sportpesa", "betika", "casino",
            "swimming", "gym day pass", "fun", "night out",
            "birthday party", "celebration", "karaoke",
        ],
        "weight": 0.08,
    },
    "Health": {
        "amounts": (100, 50000),
        "common_amounts": [200, 500, 1000, 2000, 3000, 5000, 10000, 20000],
        "descriptions": [
            "hospital", "clinic", "doctor", "consultation",
            "pharmacy", "medicine", "drugs", "paracetamol",
            "antibiotics", "prescription", "lab test", "blood test",
            "x-ray", "scan", "ultrasound", "dental", "dentist",
            "eye clinic", "glasses", "optician", "physiotherapy",
            "nhif", "insurance", "medical cover", "checkup",
            "emergency", "ambulance", "surgery", "operation",
            "mental health", "therapy", "counselling",
        ],
        "weight": 0.07,
    },
    "Education": {
        "amounts": (100, 100000),
        "common_amounts": [500, 1000, 2000, 5000, 10000, 20000, 30000, 50000],
        "descriptions": [
            "school fees", "tuition", "university", "college",
            "semester fees", "exam fees", "registration",
            "books", "textbook", "stationery", "notebook", "pen",
            "printing", "photocopy", "library", "research",
            "online course", "udemy", "coursera", "certification",
            "training", "workshop", "seminar", "conference",
            "school uniform", "school supplies", "school trip",
            "helb", "bursary", "scholarship",
        ],
        "weight": 0.07,
    },
    "Personal care": {
        "amounts": (50, 5000),
        "common_amounts": [100, 200, 300, 500, 800, 1000, 1500, 2000, 3000],
        "descriptions": [
            "haircut", "barber", "salon", "braids", "weave",
            "manicure", "pedicure", "spa", "massage", "facial",
            "lotion", "cream", "shampoo", "conditioner",
            "toothpaste", "toothbrush", "deodorant", "razor",
            "sanitary", "pads", "toiletries", "tissue",
            "gym", "fitness", "yoga", "workout", "supplements",
            "protein", "vitamins",
        ],
        "weight": 0.05,
    },
    "Other expense": {
        "amounts": (50, 20000),
        "common_amounts": [100, 200, 500, 1000, 2000, 5000, 10000],
        "descriptions": [
            "mpesa charge", "withdrawal", "transfer fee",
            "bank charge", "atm fee", "transaction fee",
            "tithe", "offering", "church", "charity", "donation",
            "harambee", "contribution", "chama", "merry go round",
            "fine", "penalty", "tax", "kra",
            "lost money", "stolen", "emergency",
            "miscellaneous", "other", "expense", "payment",
            "debt", "loan", "interest", "repayment",
        ],
        "weight": 0.05,
    },
}

# ── KES amount bins ──
AMOUNT_BIN_EDGES = [0, 100, 300, 500, 1000, 3000, 10000, 30000]
AMOUNT_BIN_LABELS = ["0-100", "100-300", "300-500", "500-1K", "1K-3K", "3K-10K", "10K-30K", "30K+"]

def amount_bin(amount):
    for label, upper in zip(AMOUNT_BIN_LABELS, AMOUNT_BIN_EDGES[1:]):
        if amount <= upper:
            return label
    return AMOUNT_BIN_LABELS[-1]

DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

def extract_keywords(desc):
    """Extract up to 3 keywords from description."""
    words = desc.lower().replace(",", "").replace(".", "").split()
    # Return individual words as keyword features
    return [w for w in words if len(w) >= 3][:3]

# ── Generate data ──
NUM_SAMPLES = 3000
data = []

start_date = datetime(2025, 1, 1)
end_date = datetime(2026, 11, 20)
date_range_days = (end_date - start_date).days

for _ in range(NUM_SAMPLES):
    # Pick category by weight
    cats = list(TEMPLATES.keys())
    weights = [TEMPLATES[c]["weight"] for c in cats]
    category = random.choices(cats, weights=weights, k=1)[0]
    t = TEMPLATES[category]

    # Generate amount
    if random.random() < 0.6:
        amount = random.choice(t["common_amounts"]) + random.randint(-20, 20)
    else:
        lo, hi = t["amounts"]
        amount = random.randint(lo, hi)
    amount = max(10, amount)

    # Generate description (1-3 words from templates)
    n_words = random.choices([1, 2, 3], weights=[0.4, 0.4, 0.2])[0]
    words = random.sample(t["descriptions"], min(n_words, len(t["descriptions"])))
    description = " ".join(words)

    # Generate date
    date = start_date + timedelta(days=random.randint(0, date_range_days))

    data.append({
        "category": category,
        "amount": amount,
        "description": description,
        "date": date,
    })

print(f"Generated {len(data)} transactions")
print("Distribution:", {k: v for k, v in sorted(Counter(d["category"] for d in data).items())})

# ── Build features ──
# Collect all keywords seen in training
all_keywords = set()
for d in data:
    all_keywords.update(extract_keywords(d["description"]))

# Keep only keywords that appear >= 5 times
keyword_counts = Counter()
for d in data:
    keyword_counts.update(extract_keywords(d["description"]))
vocab = sorted([w for w, c in keyword_counts.items() if c >= 5])
print(f"Vocabulary size: {len(vocab)} keywords")

# Feature names and their categories (possible values)
feature_specs = {
    "amount_bin": AMOUNT_BIN_LABELS,
    "day_of_week": DAY_NAMES,
    "month": [str(m) for m in range(1, 13)],
    "is_weekend": ["0", "1"],
}
# Add each keyword as a binary feature
for kw in vocab:
    feature_specs[f"kw_{kw}"] = ["0", "1"]

feature_names = list(feature_specs.keys())
feature_categories = {name: vals for name, vals in feature_specs.items()}

def encode(d):
    """Convert a data point to feature dict."""
    kws = set(extract_keywords(d["description"]))
    result = {
        "amount_bin": amount_bin(d["amount"]),
        "day_of_week": DAY_NAMES[d["date"].weekday()],
        "month": str(d["date"].month),
        "is_weekend": "1" if d["date"].weekday() >= 5 else "0",
    }
    for kw in vocab:
        result[f"kw_{kw}"] = "1" if kw in kws else "0"
    return result

# ── Train Multinomial Naive Bayes ──
classes = sorted(set(d["category"] for d in data))
n_classes = len(classes)
class_to_idx = {c: i for i, c in enumerate(classes)}

alpha = 1.0  # Laplace smoothing

# Count classes
class_counts = Counter(d["category"] for d in data)
total = len(data)
class_log_prior = [math.log(class_counts[c] / total) for c in classes]

# Build feature columns: for each feature, one column per possible value
columns = []
for fname in feature_names:
    for val in feature_categories[fname]:
        columns.append((fname, val))

n_cols = len(columns)
col_index = {(fname, val): i for i, (fname, val) in enumerate(columns)}

# Count feature occurrences per class
# feature_count[class_idx][col_idx] = count
feature_count = [[0] * n_cols for _ in range(n_classes)]
class_feature_total = [0] * n_classes

for d in data:
    ci = class_to_idx[d["category"]]
    features = encode(d)
    for fname in feature_names:
        val = features[fname]
        key = (fname, val)
        if key in col_index:
            feature_count[ci][col_index[key]] += 1
            class_feature_total[ci] += 1

# Compute log probabilities with Laplace smoothing
feature_log_prob = []
for ci in range(n_classes):
    row = []
    denom = class_feature_total[ci] + alpha * n_cols
    for j in range(n_cols):
        prob = (feature_count[ci][j] + alpha) / denom
        row.append(math.log(prob))
    feature_log_prob.append(row)

# ── Evaluate accuracy ──
correct = 0
confusion = Counter()
for d in data:
    features = encode(d)
    scores = list(class_log_prior)
    for fname in feature_names:
        val = features[fname]
        key = (fname, val)
        if key in col_index:
            j = col_index[key]
            for ci in range(n_classes):
                scores[ci] += feature_log_prob[ci][j]
    pred_idx = scores.index(max(scores))
    pred = classes[pred_idx]
    actual = d["category"]
    if pred == actual:
        correct += 1
    confusion[(actual, pred)] += 1

accuracy = correct / total * 100
print(f"\nTraining accuracy: {accuracy:.1f}%")

# Per-class accuracy
for c in classes:
    tp = confusion.get((c, c), 0)
    total_c = sum(v for (a, p), v in confusion.items() if a == c)
    acc = tp / total_c * 100 if total_c > 0 else 0
    print(f"  {c:20s}: {acc:5.1f}% ({tp}/{total_c})")

# ── Export model.json ──
model = {
    "version": "3.0",
    "trained_at": datetime.now().isoformat(),
    "model": "MultinomialNB",
    "alpha": alpha,
    "classes": classes,
    "class_log_prior": class_log_prior,
    "features": feature_names,
    "categories": feature_categories,
    "feature_log_prob": feature_log_prob,
    "amount_bin_edges": AMOUNT_BIN_EDGES,
    "amount_bin_labels": AMOUNT_BIN_LABELS,
    "vocabulary": vocab,
}

out_path = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "model.json")
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with open(out_path, "w") as f:
    json.dump(model, f, indent=1)

print(f"\nModel saved to {os.path.abspath(out_path)}")
print(f"Features: {len(feature_names)}, Columns: {n_cols}, Classes: {n_classes}")
print(f"Vocabulary ({len(vocab)} keywords): {vocab[:20]}...")

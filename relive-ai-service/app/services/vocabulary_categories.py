"""
The 22-category vocabulary ontology used to prompt Qwen at image-import time.
Each category becomes one separate prompt (see app/models/qwen_model.py ->
generate_vocabulary). Category text is taken directly from the locked design
spec — scope/includes/examples are reproduced deliberately so the prompts
match what was agreed, not a paraphrase that could drift over time.
"""

VOCABULARY_CATEGORIES = [
    {
        "name": "People & Human Entities",
        "scope": "All humans and groups of people.",
        "includes": "person, man, woman, boy, girl, child, adult, teenager, baby, elderly, couple, family, group, crowd, audience, character, idol",
        "excludes": "Clothing (separate category), emotion words (separate category), personal names (handled separately, never put a proper name here).",
    },
    {
        "name": "Animals & Wildlife",
        "scope": "All non-human animals and creatures.",
        "includes": "dog, cat, bird, horse, cow, tiger, insect, fish, elephant, lion, monkey, pet, wildlife, animal species and categories",
        "excludes": "",
    },
    {
        "name": "Plants & Vegetation",
        "scope": "Plant life and natural flora.",
        "includes": "tree, flower, grass, bush, plant, leaf, cactus, shrub, garden, vine, tropical, forest (specific flora if recognizable)",
        "excludes": "",
    },
    {
        "name": "Man-Made Objects",
        "scope": "Inanimate objects not covered by more specific categories below (furniture, electronics, household items, tools, toys, utensils, devices, etc).",
        "includes": "table, chair, laptop, phone, book, lamp, cup, sofa, camera",
        "excludes": "Anything that belongs more specifically in Clothing, Food, Vehicles, or Buildings categories.",
    },
    {
        "name": "Vehicles & Transportation",
        "scope": "All modes of transport.",
        "includes": "car, truck, bus, train, airplane, helicopter, boat, ship, bicycle, motorcycle, scooter, submarine, van, vehicle, motorbike",
        "excludes": "",
    },
    {
        "name": "Buildings & Structures",
        "scope": "Architectural and infrastructure elements.",
        "includes": "house, building, skyscraper, apartment, temple, church, school, stadium, bridge, road, tower, fence, wall, tunnel, highway",
        "excludes": "",
    },
    {
        "name": "Scenes & Environments",
        "scope": "Overall setting or location, indoor/outdoor and specific place.",
        "includes": "beach, office, park, classroom, city, street, forest, mountain, kitchen, stadium, countryside, desert, airport, market, station, indoor, outdoor",
        "excludes": "",
    },
    {
        "name": "Clothing & Accessories",
        "scope": "Worn or carried personal items.",
        "includes": "shirt, pants, dress, hat, jacket, shoes, socks, glasses, scarf, belt, purse, tie, watch, helmet, jewelry (necklace, ring)",
        "excludes": "",
    },
    {
        "name": "Food & Beverages",
        "scope": "Edible items and drinks.",
        "includes": "pizza, coffee, cake, rice, salad, fruit (apple, banana), vegetable (carrot, lettuce), soda, tea, wine, sandwich, soup",
        "excludes": "",
    },
    {
        "name": "Text, Signs & Logos",
        "scope": "Any written or symbolic markings visible in the image.",
        "includes": "STOP sign, billboard, Nike logo, graffiti, road sign, license plate, text, sign, menu, street name",
        "excludes": "",
    },
    {
        "name": "Colors & Visual Attributes",
        "scope": "Color and basic visual descriptors of the image or its subjects.",
        "includes": "red, blue, green, black, white, pink, multicolored, metallic, neon, bright, dark, colorful, pastel, black-and-white",
        "excludes": "",
    },
    {
        "name": "Materials & Textures",
        "scope": "What objects appear made of, or how surfaces look/feel.",
        "includes": "wooden, metallic, glass, leather, concrete, plastic, cloth, stone, ceramic, smooth, rough, shiny, matte, fuzzy, transparent",
        "excludes": "",
    },
    {
        "name": "Shapes & Patterns",
        "scope": "Geometric shapes, patterns and motifs.",
        "includes": "circular, square, rectangular, triangular, cylindrical, striped, spotted, floral pattern, grid, camouflage, polka-dot, checkered, symmetrical, asymmetrical",
        "excludes": "",
    },
    {
        "name": "Size & Quantity",
        "scope": "Relative size and numeric quantity descriptors.",
        "includes": "large, small, tall, short, giant, tiny, one, two, several, many, few, group, pair, multiple, single",
        "excludes": "",
    },
    {
        "name": "Condition & State",
        "scope": "Physical or operational state of objects.",
        "includes": "new, old, broken, damaged, open, closed, clean, dirty, wet, dry, vacant, occupied, static, moving, parked",
        "excludes": "",
    },
    {
        "name": "Actions & Activities",
        "scope": "Verbs describing what entities in the image are doing.",
        "includes": "running, walking, sitting, standing, eating, drinking, sleeping, cooking, reading, writing, talking, playing, driving, climbing, holding, pushing, pulling, dancing, swimming",
        "excludes": "",
    },
    {
        "name": "Spatial Relations & Interactions",
        "scope": "How objects or people relate or interact spatially — this forms the basis of scene-graph-style retrieval.",
        "includes": "next to, behind, above, below, near, inside, outside, on top of, under, touching, holding, hugging, sitting on, leaning against, standing in front of",
        "excludes": "",
    },
    {
        "name": "Emotions & Expressions",
        "scope": "Facial expressions and inferred emotions or moods of people/animals in the image.",
        "includes": "smiling, sad, angry, happy, surprised, excited, frowning, laughing, calm, relaxed, scared, worried, joyful, bored",
        "excludes": "",
    },
    {
        "name": "Events & Occasions",
        "scope": "Recognizable events, social occasions or situations depicted.",
        "includes": "wedding, birthday party, concert, meeting, protest, parade, picnic, conference, festival, ceremony, graduation, celebration, competition",
        "excludes": "",
    },
    {
        "name": "Abstract Concepts & Themes",
        "scope": "High-level concepts or themes evoked by the scene as a whole (thematic meaning, not literal objects).",
        "includes": "love, happiness, freedom, nature, work, leisure, danger, urban life, friendship, travel, technology, business, relaxation, nostalgia, solitude, adventure",
        "excludes": "",
    },
    {
        "name": "Weather & Time-of-Day",
        "scope": "Environmental conditions, lighting, and temporal setting visible in the image.",
        "includes": "sunny, rainy, night, sunset, snowy, cloudy, foggy, stormy, morning, afternoon, evening, dawn, dusk, sunrise, winter, summer (only if visually obvious)",
        "excludes": "",
    },
    {
        "name": "Image Style & Composition",
        "scope": "The way the image itself is presented or composed, not its subject matter.",
        "includes": "photograph, black and white, cartoon, aerial view, close-up, portrait, landscape orientation, vintage, pixelated, macro shot, panoramic, blurry, high-contrast, HDR, selfie, screenshot",
        "excludes": "",
    },
]

assert len(VOCABULARY_CATEGORIES) == 22
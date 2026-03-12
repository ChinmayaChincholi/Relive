import re


def normalize_object(obj):

    obj = obj.lower().strip()

    obj = re.sub(r"^(a|an|the)\s+", "", obj)

    if obj.endswith("s") and not obj.endswith("ss"):
        obj = obj[:-1]

    return obj
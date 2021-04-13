from FeatureExtractor import FeatureExtractor
from WordEncoder import WordEncoder


class PretrainedModels():
    def __init__(self):
        self.featureExtractor = FeatureExtractor()
        self.wordEncoder = WordEncoder()

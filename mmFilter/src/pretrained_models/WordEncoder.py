from transformers import BertTokenizer, BertModel, BertConfig
import torch.nn as nn
import torch


class WordEncoder():
    '''
    This class uses the bert model as word encoder
    '''
    def __init__(self):
        self.tokenizer = BertTokenizer.from_pretrained('bert-base-uncased')
        self.configuration = BertConfig()
        self.model = BertModel(self.configuration)
        self.featureExtractor = nn.Linear(768, 256)

    def run(self, sentence=None):
        if sentence is None:
            sentence = "My cat is scratching the sofa."
        inputs = self.tokenizer(sentence, return_tensors="pt")
        outputs = self.model(**inputs)
        last_hidden_states = outputs.last_hidden_state
        h2 = torch.mean(last_hidden_states, dim=1).squeeze()
        query_embedding = self.featureExtractor(h2)
        return query_embedding.detach().numpy()


if __name__ == "__main__":
    we = WordEncoder()
    we.run()

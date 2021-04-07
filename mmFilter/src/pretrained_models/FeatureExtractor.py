import torch
import torch.nn as nn


class FeatureExtractor():
    '''
    The class uses the pretrained model to do the extraction
    '''
    def __init__(self):
        mobilenet_v2 = torch.hub.load('pytorch/vision:v0.5.0', 'mobilenet_v2', pretrained=False)
        mobilenet_v2 = list(mobilenet_v2.children())[:-1]  # Remove the last FC(2048, 1000)
        self.model = nn.Sequential(
            *mobilenet_v2,
        )
        self.model.eval()
        self.rslayer = nn.Linear(1280*8*8, 256)

    def run(self, input_data=None):
        if input_data is None:
            input_data = torch.rand(12, 3, 256, 256, dtype=torch.float32)
        input_data = torch.from_numpy(input_data).float()

        h1 = self.model(input_data)
        output_data = self.rslayer(torch.flatten(h1, start_dim=1))
        return output_data.detach().numpy()


if __name__ == "__main__":
    fe = FeatureExtractor()
    print(fe.run().shape)
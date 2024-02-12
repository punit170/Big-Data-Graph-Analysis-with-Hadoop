import graphviz
import os


shards_folder_path = 'C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/'

# Specify the path to your DOT file
dot_file_path = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph50.ngs.dot"
# dot_file_path = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/Net Graph - shard0.ngs.dot"
# dot_file_path = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/Graph50.ngs.perturbed.dot"

# Read and parse the DOT file
graph = graphviz.Source.from_file(dot_file_path)

# Display the parsed graph
graph.view()
# Save the parsed graph as a PDF file
graph.render(filename="output", format="pdf")

# i = 0
# for filename in os.listdir(shards_folder_path):
#     if filename.endswith(".dot"):
#         shard_file_path = os.path.join(shards_folder_path, filename)
        
#         # Read and parse the DOT file
#         graph = graphviz.Source.from_file(shard_file_path)

#         # Display the parsed graph
#         graph.view()
#         # Save the parsed graph as a PDF file
#         graph.render(filename=f"shard{i}", format="pdf")
#         i +=1

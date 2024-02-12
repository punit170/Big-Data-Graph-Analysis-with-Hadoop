i = 0
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

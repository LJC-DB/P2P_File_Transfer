# P2P File Transfer

Para executar os arquivos, utilizar o comando: 

    javac -cp '.:gson.jar' p2p_file_transfer/Server.java && java -cp '.:gson.jar' p2p_file_transfer/Server
para compilar e executar o servidor e o comando:

    javac -cp '.:gson.jar' p2p_file_transfer/Peer.java && java -cp '.:gson.jar' p2p_file_transfer/Peer
para o peer.

O local de execução destes comandos é na pasta pai de *p2p_file_transfer* (pasta do repositório).

Os comandos acima são utilizados no Ubuntu e talvez seja necessário seu ajuste caso forem ser utilizados em outro sistema operacional, como a mudança de */* por *\\* para indicar o diretório.

A execução correta destes arquivos necessita do arquivo *gson.jar* no local de execução. Este arquivo pode ser obtido em https://mvnrepository.com/artifact/com.google.code.gson/gson.

A pasta para salvamento dos arquivos que deve ser fornecida durante a execução é relativa ao local da execução e deve existir previamente, por exemplo *p2p_file_transfer/folder* para uma pasta localizada dentro de *./p2p_file_transfer*.

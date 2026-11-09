# LANDING (rascunho de pouso) — base-ficticia-12k

Destino no repositório: `seed/base-ficticia/` (FORA de qualquer `db/migration`).
Regra dos lotes: arquivos de documentação/gerador/limpeza sobem nos lotes iniciais;
cada `carga-NNNN.sql` sobe **sozinho** numa chamada de push (≤ ~110 KB por chamada), na ordem numérica.
O Portão (portao-deploy.py) re-verifica revisão, confirmação, rebase e testes antes de qualquer push.

## Lote 0 — documentos e scripts (1 chamada por arquivo, nesta ordem)

1. `entrega/seed/base-ficticia/LEIA-ME.md` → `seed/base-ficticia/LEIA-ME.md`
2. `entrega/seed/base-ficticia/RELATORIO-COBERTURA.md` → `seed/base-ficticia/RELATORIO-COBERTURA.md`
3. `entrega/seed/base-ficticia/gerar-base-ficticia.py` → `seed/base-ficticia/gerar-base-ficticia.py`
4. `entrega/seed/base-ficticia/00-tenant-ficticio.sql` → `seed/base-ficticia/00-tenant-ficticio.sql`
5. `entrega/seed/base-ficticia/limpeza-base-ficticia.sql` → `seed/base-ficticia/limpeza-base-ficticia.sql`
6. `entrega/seed/base-ficticia/prova-carga-limpeza.sh` → `seed/base-ficticia/prova-carga-limpeza.sh`
7. `entrega/seed/base-ficticia/matriz.csv` → `seed/base-ficticia/matriz.csv`
8. `entrega/seed/base-ficticia/manifesto.json` → `seed/base-ficticia/manifesto.json`

## Lotes 1..307 — massa (1 arquivo por chamada, nesta ordem)

1. `entrega/seed/base-ficticia/carga-0001.sql` → `seed/base-ficticia/carga-0001.sql` (62708 bytes)
2. `entrega/seed/base-ficticia/carga-0002.sql` → `seed/base-ficticia/carga-0002.sql` (99910 bytes)
3. `entrega/seed/base-ficticia/carga-0003.sql` → `seed/base-ficticia/carga-0003.sql` (85303 bytes)
4. `entrega/seed/base-ficticia/carga-0004.sql` → `seed/base-ficticia/carga-0004.sql` (60396 bytes)
5. `entrega/seed/base-ficticia/carga-0005.sql` → `seed/base-ficticia/carga-0005.sql` (41178 bytes)
6. `entrega/seed/base-ficticia/carga-0006.sql` → `seed/base-ficticia/carga-0006.sql` (71264 bytes)
7. `entrega/seed/base-ficticia/carga-0007.sql` → `seed/base-ficticia/carga-0007.sql` (89666 bytes)
8. `entrega/seed/base-ficticia/carga-0008.sql` → `seed/base-ficticia/carga-0008.sql` (74382 bytes)
9. `entrega/seed/base-ficticia/carga-0009.sql` → `seed/base-ficticia/carga-0009.sql` (76882 bytes)
10. `entrega/seed/base-ficticia/carga-0010.sql` → `seed/base-ficticia/carga-0010.sql` (99820 bytes)
11. `entrega/seed/base-ficticia/carga-0011.sql` → `seed/base-ficticia/carga-0011.sql` (61154 bytes)
12. `entrega/seed/base-ficticia/carga-0012.sql` → `seed/base-ficticia/carga-0012.sql` (99923 bytes)
13. `entrega/seed/base-ficticia/carga-0013.sql` → `seed/base-ficticia/carga-0013.sql` (85598 bytes)
14. `entrega/seed/base-ficticia/carga-0014.sql` → `seed/base-ficticia/carga-0014.sql` (60396 bytes)
15. `entrega/seed/base-ficticia/carga-0015.sql` → `seed/base-ficticia/carga-0015.sql` (41178 bytes)
16. `entrega/seed/base-ficticia/carga-0016.sql` → `seed/base-ficticia/carga-0016.sql` (71264 bytes)
17. `entrega/seed/base-ficticia/carga-0017.sql` → `seed/base-ficticia/carga-0017.sql` (89666 bytes)
18. `entrega/seed/base-ficticia/carga-0018.sql` → `seed/base-ficticia/carga-0018.sql` (74372 bytes)
19. `entrega/seed/base-ficticia/carga-0019.sql` → `seed/base-ficticia/carga-0019.sql` (76873 bytes)
20. `entrega/seed/base-ficticia/carga-0020.sql` → `seed/base-ficticia/carga-0020.sql` (99792 bytes)
21. `entrega/seed/base-ficticia/carga-0021.sql` → `seed/base-ficticia/carga-0021.sql` (60995 bytes)
22. `entrega/seed/base-ficticia/carga-0022.sql` → `seed/base-ficticia/carga-0022.sql` (99765 bytes)
23. `entrega/seed/base-ficticia/carga-0023.sql` → `seed/base-ficticia/carga-0023.sql` (85957 bytes)
24. `entrega/seed/base-ficticia/carga-0024.sql` → `seed/base-ficticia/carga-0024.sql` (60396 bytes)
25. `entrega/seed/base-ficticia/carga-0025.sql` → `seed/base-ficticia/carga-0025.sql` (41178 bytes)
26. `entrega/seed/base-ficticia/carga-0026.sql` → `seed/base-ficticia/carga-0026.sql` (71264 bytes)
27. `entrega/seed/base-ficticia/carga-0027.sql` → `seed/base-ficticia/carga-0027.sql` (89666 bytes)
28. `entrega/seed/base-ficticia/carga-0028.sql` → `seed/base-ficticia/carga-0028.sql` (74366 bytes)
29. `entrega/seed/base-ficticia/carga-0029.sql` → `seed/base-ficticia/carga-0029.sql` (76922 bytes)
30. `entrega/seed/base-ficticia/carga-0030.sql` → `seed/base-ficticia/carga-0030.sql` (99854 bytes)
31. `entrega/seed/base-ficticia/carga-0031.sql` → `seed/base-ficticia/carga-0031.sql` (61192 bytes)
32. `entrega/seed/base-ficticia/carga-0032.sql` → `seed/base-ficticia/carga-0032.sql` (99964 bytes)
33. `entrega/seed/base-ficticia/carga-0033.sql` → `seed/base-ficticia/carga-0033.sql` (85957 bytes)
34. `entrega/seed/base-ficticia/carga-0034.sql` → `seed/base-ficticia/carga-0034.sql` (60396 bytes)
35. `entrega/seed/base-ficticia/carga-0035.sql` → `seed/base-ficticia/carga-0035.sql` (41178 bytes)
36. `entrega/seed/base-ficticia/carga-0036.sql` → `seed/base-ficticia/carga-0036.sql` (71264 bytes)
37. `entrega/seed/base-ficticia/carga-0037.sql` → `seed/base-ficticia/carga-0037.sql` (89666 bytes)
38. `entrega/seed/base-ficticia/carga-0038.sql` → `seed/base-ficticia/carga-0038.sql` (74356 bytes)
39. `entrega/seed/base-ficticia/carga-0039.sql` → `seed/base-ficticia/carga-0039.sql` (76863 bytes)
40. `entrega/seed/base-ficticia/carga-0040.sql` → `seed/base-ficticia/carga-0040.sql` (99784 bytes)
41. `entrega/seed/base-ficticia/carga-0041.sql` → `seed/base-ficticia/carga-0041.sql` (61203 bytes)
42. `entrega/seed/base-ficticia/carga-0042.sql` → `seed/base-ficticia/carga-0042.sql` (99964 bytes)
43. `entrega/seed/base-ficticia/carga-0043.sql` → `seed/base-ficticia/carga-0043.sql` (85957 bytes)
44. `entrega/seed/base-ficticia/carga-0044.sql` → `seed/base-ficticia/carga-0044.sql` (60396 bytes)
45. `entrega/seed/base-ficticia/carga-0045.sql` → `seed/base-ficticia/carga-0045.sql` (41178 bytes)
46. `entrega/seed/base-ficticia/carga-0046.sql` → `seed/base-ficticia/carga-0046.sql` (71264 bytes)
47. `entrega/seed/base-ficticia/carga-0047.sql` → `seed/base-ficticia/carga-0047.sql` (89666 bytes)
48. `entrega/seed/base-ficticia/carga-0048.sql` → `seed/base-ficticia/carga-0048.sql` (74370 bytes)
49. `entrega/seed/base-ficticia/carga-0049.sql` → `seed/base-ficticia/carga-0049.sql` (76500 bytes)
50. `entrega/seed/base-ficticia/carga-0050.sql` → `seed/base-ficticia/carga-0050.sql` (99825 bytes)
51. `entrega/seed/base-ficticia/carga-0051.sql` → `seed/base-ficticia/carga-0051.sql` (60989 bytes)
52. `entrega/seed/base-ficticia/carga-0052.sql` → `seed/base-ficticia/carga-0052.sql` (99706 bytes)
53. `entrega/seed/base-ficticia/carga-0053.sql` → `seed/base-ficticia/carga-0053.sql` (86615 bytes)
54. `entrega/seed/base-ficticia/carga-0054.sql` → `seed/base-ficticia/carga-0054.sql` (60396 bytes)
55. `entrega/seed/base-ficticia/carga-0055.sql` → `seed/base-ficticia/carga-0055.sql` (41178 bytes)
56. `entrega/seed/base-ficticia/carga-0056.sql` → `seed/base-ficticia/carga-0056.sql` (71264 bytes)
57. `entrega/seed/base-ficticia/carga-0057.sql` → `seed/base-ficticia/carga-0057.sql` (89666 bytes)
58. `entrega/seed/base-ficticia/carga-0058.sql` → `seed/base-ficticia/carga-0058.sql` (74372 bytes)
59. `entrega/seed/base-ficticia/carga-0059.sql` → `seed/base-ficticia/carga-0059.sql` (76093 bytes)
60. `entrega/seed/base-ficticia/carga-0060.sql` → `seed/base-ficticia/carga-0060.sql` (99824 bytes)
61. `entrega/seed/base-ficticia/carga-0061.sql` → `seed/base-ficticia/carga-0061.sql` (61193 bytes)
62. `entrega/seed/base-ficticia/carga-0062.sql` → `seed/base-ficticia/carga-0062.sql` (99706 bytes)
63. `entrega/seed/base-ficticia/carga-0063.sql` → `seed/base-ficticia/carga-0063.sql` (86615 bytes)
64. `entrega/seed/base-ficticia/carga-0064.sql` → `seed/base-ficticia/carga-0064.sql` (60396 bytes)
65. `entrega/seed/base-ficticia/carga-0065.sql` → `seed/base-ficticia/carga-0065.sql` (41178 bytes)
66. `entrega/seed/base-ficticia/carga-0066.sql` → `seed/base-ficticia/carga-0066.sql` (71264 bytes)
67. `entrega/seed/base-ficticia/carga-0067.sql` → `seed/base-ficticia/carga-0067.sql` (89666 bytes)
68. `entrega/seed/base-ficticia/carga-0068.sql` → `seed/base-ficticia/carga-0068.sql` (74352 bytes)
69. `entrega/seed/base-ficticia/carga-0069.sql` → `seed/base-ficticia/carga-0069.sql` (76093 bytes)
70. `entrega/seed/base-ficticia/carga-0070.sql` → `seed/base-ficticia/carga-0070.sql` (99828 bytes)
71. `entrega/seed/base-ficticia/carga-0071.sql` → `seed/base-ficticia/carga-0071.sql` (61189 bytes)
72. `entrega/seed/base-ficticia/carga-0072.sql` → `seed/base-ficticia/carga-0072.sql` (99706 bytes)
73. `entrega/seed/base-ficticia/carga-0073.sql` → `seed/base-ficticia/carga-0073.sql` (86615 bytes)
74. `entrega/seed/base-ficticia/carga-0074.sql` → `seed/base-ficticia/carga-0074.sql` (60396 bytes)
75. `entrega/seed/base-ficticia/carga-0075.sql` → `seed/base-ficticia/carga-0075.sql` (41178 bytes)
76. `entrega/seed/base-ficticia/carga-0076.sql` → `seed/base-ficticia/carga-0076.sql` (71264 bytes)
77. `entrega/seed/base-ficticia/carga-0077.sql` → `seed/base-ficticia/carga-0077.sql` (89666 bytes)
78. `entrega/seed/base-ficticia/carga-0078.sql` → `seed/base-ficticia/carga-0078.sql` (74394 bytes)
79. `entrega/seed/base-ficticia/carga-0079.sql` → `seed/base-ficticia/carga-0079.sql` (76110 bytes)
80. `entrega/seed/base-ficticia/carga-0080.sql` → `seed/base-ficticia/carga-0080.sql` (99842 bytes)
81. `entrega/seed/base-ficticia/carga-0081.sql` → `seed/base-ficticia/carga-0081.sql` (60982 bytes)
82. `entrega/seed/base-ficticia/carga-0082.sql` → `seed/base-ficticia/carga-0082.sql` (99706 bytes)
83. `entrega/seed/base-ficticia/carga-0083.sql` → `seed/base-ficticia/carga-0083.sql` (86615 bytes)
84. `entrega/seed/base-ficticia/carga-0084.sql` → `seed/base-ficticia/carga-0084.sql` (60396 bytes)
85. `entrega/seed/base-ficticia/carga-0085.sql` → `seed/base-ficticia/carga-0085.sql` (41178 bytes)
86. `entrega/seed/base-ficticia/carga-0086.sql` → `seed/base-ficticia/carga-0086.sql` (71264 bytes)
87. `entrega/seed/base-ficticia/carga-0087.sql` → `seed/base-ficticia/carga-0087.sql` (89666 bytes)
88. `entrega/seed/base-ficticia/carga-0088.sql` → `seed/base-ficticia/carga-0088.sql` (74388 bytes)
89. `entrega/seed/base-ficticia/carga-0089.sql` → `seed/base-ficticia/carga-0089.sql` (76685 bytes)
90. `entrega/seed/base-ficticia/carga-0090.sql` → `seed/base-ficticia/carga-0090.sql` (99820 bytes)
91. `entrega/seed/base-ficticia/carga-0091.sql` → `seed/base-ficticia/carga-0091.sql` (99037 bytes)
92. `entrega/seed/base-ficticia/carga-0092.sql` → `seed/base-ficticia/carga-0092.sql` (14515 bytes)
93. `entrega/seed/base-ficticia/carga-0093.sql` → `seed/base-ficticia/carga-0093.sql` (99706 bytes)
94. `entrega/seed/base-ficticia/carga-0094.sql` → `seed/base-ficticia/carga-0094.sql` (86615 bytes)
95. `entrega/seed/base-ficticia/carga-0095.sql` → `seed/base-ficticia/carga-0095.sql` (60396 bytes)
96. `entrega/seed/base-ficticia/carga-0096.sql` → `seed/base-ficticia/carga-0096.sql` (41178 bytes)
97. `entrega/seed/base-ficticia/carga-0097.sql` → `seed/base-ficticia/carga-0097.sql` (71264 bytes)
98. `entrega/seed/base-ficticia/carga-0098.sql` → `seed/base-ficticia/carga-0098.sql` (89666 bytes)
99. `entrega/seed/base-ficticia/carga-0099.sql` → `seed/base-ficticia/carga-0099.sql` (74378 bytes)
100. `entrega/seed/base-ficticia/carga-0100.sql` → `seed/base-ficticia/carga-0100.sql` (76880 bytes)
101. `entrega/seed/base-ficticia/carga-0101.sql` → `seed/base-ficticia/carga-0101.sql` (99807 bytes)
102. `entrega/seed/base-ficticia/carga-0102.sql` → `seed/base-ficticia/carga-0102.sql` (76280 bytes)
103. `entrega/seed/base-ficticia/carga-0103.sql` → `seed/base-ficticia/carga-0103.sql` (54580 bytes)
104. `entrega/seed/base-ficticia/carga-0104.sql` → `seed/base-ficticia/carga-0104.sql` (99706 bytes)
105. `entrega/seed/base-ficticia/carga-0105.sql` → `seed/base-ficticia/carga-0105.sql` (86615 bytes)
106. `entrega/seed/base-ficticia/carga-0106.sql` → `seed/base-ficticia/carga-0106.sql` (60396 bytes)
107. `entrega/seed/base-ficticia/carga-0107.sql` → `seed/base-ficticia/carga-0107.sql` (41178 bytes)
108. `entrega/seed/base-ficticia/carga-0108.sql` → `seed/base-ficticia/carga-0108.sql` (71264 bytes)
109. `entrega/seed/base-ficticia/carga-0109.sql` → `seed/base-ficticia/carga-0109.sql` (89666 bytes)
110. `entrega/seed/base-ficticia/carga-0110.sql` → `seed/base-ficticia/carga-0110.sql` (74376 bytes)
111. `entrega/seed/base-ficticia/carga-0111.sql` → `seed/base-ficticia/carga-0111.sql` (76883 bytes)
112. `entrega/seed/base-ficticia/carga-0112.sql` → `seed/base-ficticia/carga-0112.sql` (99811 bytes)
113. `entrega/seed/base-ficticia/carga-0113.sql` → `seed/base-ficticia/carga-0113.sql` (76279 bytes)
114. `entrega/seed/base-ficticia/carga-0114.sql` → `seed/base-ficticia/carga-0114.sql` (97397 bytes)
115. `entrega/seed/base-ficticia/carga-0115.sql` → `seed/base-ficticia/carga-0115.sql` (72170 bytes)
116. `entrega/seed/base-ficticia/carga-0116.sql` → `seed/base-ficticia/carga-0116.sql` (99706 bytes)
117. `entrega/seed/base-ficticia/carga-0117.sql` → `seed/base-ficticia/carga-0117.sql` (86615 bytes)
118. `entrega/seed/base-ficticia/carga-0118.sql` → `seed/base-ficticia/carga-0118.sql` (60396 bytes)
119. `entrega/seed/base-ficticia/carga-0119.sql` → `seed/base-ficticia/carga-0119.sql` (62134 bytes)
120. `entrega/seed/base-ficticia/carga-0120.sql` → `seed/base-ficticia/carga-0120.sql` (41178 bytes)
121. `entrega/seed/base-ficticia/carga-0121.sql` → `seed/base-ficticia/carga-0121.sql` (71264 bytes)
122. `entrega/seed/base-ficticia/carga-0122.sql` → `seed/base-ficticia/carga-0122.sql` (89666 bytes)
123. `entrega/seed/base-ficticia/carga-0123.sql` → `seed/base-ficticia/carga-0123.sql` (74372 bytes)
124. `entrega/seed/base-ficticia/carga-0124.sql` → `seed/base-ficticia/carga-0124.sql` (76885 bytes)
125. `entrega/seed/base-ficticia/carga-0125.sql` → `seed/base-ficticia/carga-0125.sql` (99809 bytes)
126. `entrega/seed/base-ficticia/carga-0126.sql` → `seed/base-ficticia/carga-0126.sql` (41683 bytes)
127. `entrega/seed/base-ficticia/carga-0127.sql` → `seed/base-ficticia/carga-0127.sql` (97397 bytes)
128. `entrega/seed/base-ficticia/carga-0128.sql` → `seed/base-ficticia/carga-0128.sql` (72380 bytes)
129. `entrega/seed/base-ficticia/carga-0129.sql` → `seed/base-ficticia/carga-0129.sql` (99706 bytes)
130. `entrega/seed/base-ficticia/carga-0130.sql` → `seed/base-ficticia/carga-0130.sql` (86615 bytes)
131. `entrega/seed/base-ficticia/carga-0131.sql` → `seed/base-ficticia/carga-0131.sql` (60396 bytes)
132. `entrega/seed/base-ficticia/carga-0132.sql` → `seed/base-ficticia/carga-0132.sql` (99710 bytes)
133. `entrega/seed/base-ficticia/carga-0133.sql` → `seed/base-ficticia/carga-0133.sql` (65431 bytes)
134. `entrega/seed/base-ficticia/carga-0134.sql` → `seed/base-ficticia/carga-0134.sql` (71264 bytes)
135. `entrega/seed/base-ficticia/carga-0135.sql` → `seed/base-ficticia/carga-0135.sql` (89666 bytes)
136. `entrega/seed/base-ficticia/carga-0136.sql` → `seed/base-ficticia/carga-0136.sql` (74376 bytes)
137. `entrega/seed/base-ficticia/carga-0137.sql` → `seed/base-ficticia/carga-0137.sql` (76895 bytes)
138. `entrega/seed/base-ficticia/carga-0138.sql` → `seed/base-ficticia/carga-0138.sql` (99825 bytes)
139. `entrega/seed/base-ficticia/carga-0139.sql` → `seed/base-ficticia/carga-0139.sql` (61194 bytes)
140. `entrega/seed/base-ficticia/carga-0140.sql` → `seed/base-ficticia/carga-0140.sql` (99706 bytes)
141. `entrega/seed/base-ficticia/carga-0141.sql` → `seed/base-ficticia/carga-0141.sql` (86615 bytes)
142. `entrega/seed/base-ficticia/carga-0142.sql` → `seed/base-ficticia/carga-0142.sql` (60396 bytes)
143. `entrega/seed/base-ficticia/carga-0143.sql` → `seed/base-ficticia/carga-0143.sql` (99710 bytes)
144. `entrega/seed/base-ficticia/carga-0144.sql` → `seed/base-ficticia/carga-0144.sql` (65431 bytes)
145. `entrega/seed/base-ficticia/carga-0145.sql` → `seed/base-ficticia/carga-0145.sql` (71264 bytes)
146. `entrega/seed/base-ficticia/carga-0146.sql` → `seed/base-ficticia/carga-0146.sql` (89666 bytes)
147. `entrega/seed/base-ficticia/carga-0147.sql` → `seed/base-ficticia/carga-0147.sql` (74392 bytes)
148. `entrega/seed/base-ficticia/carga-0148.sql` → `seed/base-ficticia/carga-0148.sql` (76917 bytes)
149. `entrega/seed/base-ficticia/carga-0149.sql` → `seed/base-ficticia/carga-0149.sql` (99851 bytes)
150. `entrega/seed/base-ficticia/carga-0150.sql` → `seed/base-ficticia/carga-0150.sql` (99038 bytes)
151. `entrega/seed/base-ficticia/carga-0151.sql` → `seed/base-ficticia/carga-0151.sql` (14306 bytes)
152. `entrega/seed/base-ficticia/carga-0152.sql` → `seed/base-ficticia/carga-0152.sql` (99706 bytes)
153. `entrega/seed/base-ficticia/carga-0153.sql` → `seed/base-ficticia/carga-0153.sql` (86615 bytes)
154. `entrega/seed/base-ficticia/carga-0154.sql` → `seed/base-ficticia/carga-0154.sql` (60396 bytes)
155. `entrega/seed/base-ficticia/carga-0155.sql` → `seed/base-ficticia/carga-0155.sql` (41178 bytes)
156. `entrega/seed/base-ficticia/carga-0156.sql` → `seed/base-ficticia/carga-0156.sql` (71264 bytes)
157. `entrega/seed/base-ficticia/carga-0157.sql` → `seed/base-ficticia/carga-0157.sql` (89666 bytes)
158. `entrega/seed/base-ficticia/carga-0158.sql` → `seed/base-ficticia/carga-0158.sql` (74374 bytes)
159. `entrega/seed/base-ficticia/carga-0159.sql` → `seed/base-ficticia/carga-0159.sql` (76898 bytes)
160. `entrega/seed/base-ficticia/carga-0160.sql` → `seed/base-ficticia/carga-0160.sql` (99832 bytes)
161. `entrega/seed/base-ficticia/carga-0161.sql` → `seed/base-ficticia/carga-0161.sql` (61257 bytes)
162. `entrega/seed/base-ficticia/carga-0162.sql` → `seed/base-ficticia/carga-0162.sql` (99706 bytes)
163. `entrega/seed/base-ficticia/carga-0163.sql` → `seed/base-ficticia/carga-0163.sql` (86615 bytes)
164. `entrega/seed/base-ficticia/carga-0164.sql` → `seed/base-ficticia/carga-0164.sql` (60396 bytes)
165. `entrega/seed/base-ficticia/carga-0165.sql` → `seed/base-ficticia/carga-0165.sql` (41178 bytes)
166. `entrega/seed/base-ficticia/carga-0166.sql` → `seed/base-ficticia/carga-0166.sql` (71264 bytes)
167. `entrega/seed/base-ficticia/carga-0167.sql` → `seed/base-ficticia/carga-0167.sql` (89666 bytes)
168. `entrega/seed/base-ficticia/carga-0168.sql` → `seed/base-ficticia/carga-0168.sql` (74352 bytes)
169. `entrega/seed/base-ficticia/carga-0169.sql` → `seed/base-ficticia/carga-0169.sql` (76877 bytes)
170. `entrega/seed/base-ficticia/carga-0170.sql` → `seed/base-ficticia/carga-0170.sql` (99795 bytes)
171. `entrega/seed/base-ficticia/carga-0171.sql` → `seed/base-ficticia/carga-0171.sql` (61273 bytes)
172. `entrega/seed/base-ficticia/carga-0172.sql` → `seed/base-ficticia/carga-0172.sql` (99706 bytes)
173. `entrega/seed/base-ficticia/carga-0173.sql` → `seed/base-ficticia/carga-0173.sql` (86615 bytes)
174. `entrega/seed/base-ficticia/carga-0174.sql` → `seed/base-ficticia/carga-0174.sql` (60396 bytes)
175. `entrega/seed/base-ficticia/carga-0175.sql` → `seed/base-ficticia/carga-0175.sql` (41178 bytes)
176. `entrega/seed/base-ficticia/carga-0176.sql` → `seed/base-ficticia/carga-0176.sql` (71264 bytes)
177. `entrega/seed/base-ficticia/carga-0177.sql` → `seed/base-ficticia/carga-0177.sql` (89666 bytes)
178. `entrega/seed/base-ficticia/carga-0178.sql` → `seed/base-ficticia/carga-0178.sql` (74400 bytes)
179. `entrega/seed/base-ficticia/carga-0179.sql` → `seed/base-ficticia/carga-0179.sql` (76095 bytes)
180. `entrega/seed/base-ficticia/carga-0180.sql` → `seed/base-ficticia/carga-0180.sql` (99812 bytes)
181. `entrega/seed/base-ficticia/carga-0181.sql` → `seed/base-ficticia/carga-0181.sql` (61063 bytes)
182. `entrega/seed/base-ficticia/carga-0182.sql` → `seed/base-ficticia/carga-0182.sql` (99706 bytes)
183. `entrega/seed/base-ficticia/carga-0183.sql` → `seed/base-ficticia/carga-0183.sql` (86615 bytes)
184. `entrega/seed/base-ficticia/carga-0184.sql` → `seed/base-ficticia/carga-0184.sql` (60396 bytes)
185. `entrega/seed/base-ficticia/carga-0185.sql` → `seed/base-ficticia/carga-0185.sql` (41178 bytes)
186. `entrega/seed/base-ficticia/carga-0186.sql` → `seed/base-ficticia/carga-0186.sql` (71264 bytes)
187. `entrega/seed/base-ficticia/carga-0187.sql` → `seed/base-ficticia/carga-0187.sql` (89666 bytes)
188. `entrega/seed/base-ficticia/carga-0188.sql` → `seed/base-ficticia/carga-0188.sql` (74400 bytes)
189. `entrega/seed/base-ficticia/carga-0189.sql` → `seed/base-ficticia/carga-0189.sql` (76541 bytes)
190. `entrega/seed/base-ficticia/carga-0190.sql` → `seed/base-ficticia/carga-0190.sql` (99876 bytes)
191. `entrega/seed/base-ficticia/carga-0191.sql` → `seed/base-ficticia/carga-0191.sql` (61256 bytes)
192. `entrega/seed/base-ficticia/carga-0192.sql` → `seed/base-ficticia/carga-0192.sql` (99706 bytes)
193. `entrega/seed/base-ficticia/carga-0193.sql` → `seed/base-ficticia/carga-0193.sql` (86615 bytes)
194. `entrega/seed/base-ficticia/carga-0194.sql` → `seed/base-ficticia/carga-0194.sql` (60396 bytes)
195. `entrega/seed/base-ficticia/carga-0195.sql` → `seed/base-ficticia/carga-0195.sql` (41178 bytes)
196. `entrega/seed/base-ficticia/carga-0196.sql` → `seed/base-ficticia/carga-0196.sql` (71264 bytes)
197. `entrega/seed/base-ficticia/carga-0197.sql` → `seed/base-ficticia/carga-0197.sql` (67366 bytes)
198. `entrega/seed/base-ficticia/carga-0198.sql` → `seed/base-ficticia/carga-0198.sql` (71520 bytes)
199. `entrega/seed/base-ficticia/carga-0199.sql` → `seed/base-ficticia/carga-0199.sql` (76889 bytes)
200. `entrega/seed/base-ficticia/carga-0200.sql` → `seed/base-ficticia/carga-0200.sql` (99814 bytes)
201. `entrega/seed/base-ficticia/carga-0201.sql` → `seed/base-ficticia/carga-0201.sql` (61266 bytes)
202. `entrega/seed/base-ficticia/carga-0202.sql` → `seed/base-ficticia/carga-0202.sql` (99706 bytes)
203. `entrega/seed/base-ficticia/carga-0203.sql` → `seed/base-ficticia/carga-0203.sql` (86615 bytes)
204. `entrega/seed/base-ficticia/carga-0204.sql` → `seed/base-ficticia/carga-0204.sql` (60396 bytes)
205. `entrega/seed/base-ficticia/carga-0205.sql` → `seed/base-ficticia/carga-0205.sql` (41178 bytes)
206. `entrega/seed/base-ficticia/carga-0206.sql` → `seed/base-ficticia/carga-0206.sql` (71264 bytes)
207. `entrega/seed/base-ficticia/carga-0207.sql` → `seed/base-ficticia/carga-0207.sql` (62928 bytes)
208. `entrega/seed/base-ficticia/carga-0208.sql` → `seed/base-ficticia/carga-0208.sql` (76977 bytes)
209. `entrega/seed/base-ficticia/carga-0209.sql` → `seed/base-ficticia/carga-0209.sql` (99892 bytes)
210. `entrega/seed/base-ficticia/carga-0210.sql` → `seed/base-ficticia/carga-0210.sql` (61065 bytes)
211. `entrega/seed/base-ficticia/carga-0211.sql` → `seed/base-ficticia/carga-0211.sql` (99706 bytes)
212. `entrega/seed/base-ficticia/carga-0212.sql` → `seed/base-ficticia/carga-0212.sql` (86615 bytes)
213. `entrega/seed/base-ficticia/carga-0213.sql` → `seed/base-ficticia/carga-0213.sql` (60396 bytes)
214. `entrega/seed/base-ficticia/carga-0214.sql` → `seed/base-ficticia/carga-0214.sql` (62928 bytes)
215. `entrega/seed/base-ficticia/carga-0215.sql` → `seed/base-ficticia/carga-0215.sql` (76959 bytes)
216. `entrega/seed/base-ficticia/carga-0216.sql` → `seed/base-ficticia/carga-0216.sql` (99882 bytes)
217. `entrega/seed/base-ficticia/carga-0217.sql` → `seed/base-ficticia/carga-0217.sql` (61268 bytes)
218. `entrega/seed/base-ficticia/carga-0218.sql` → `seed/base-ficticia/carga-0218.sql` (99706 bytes)
219. `entrega/seed/base-ficticia/carga-0219.sql` → `seed/base-ficticia/carga-0219.sql` (86615 bytes)
220. `entrega/seed/base-ficticia/carga-0220.sql` → `seed/base-ficticia/carga-0220.sql` (60396 bytes)
221. `entrega/seed/base-ficticia/carga-0221.sql` → `seed/base-ficticia/carga-0221.sql` (62928 bytes)
222. `entrega/seed/base-ficticia/carga-0222.sql` → `seed/base-ficticia/carga-0222.sql` (76961 bytes)
223. `entrega/seed/base-ficticia/carga-0223.sql` → `seed/base-ficticia/carga-0223.sql` (99886 bytes)
224. `entrega/seed/base-ficticia/carga-0224.sql` → `seed/base-ficticia/carga-0224.sql` (96024 bytes)
225. `entrega/seed/base-ficticia/carga-0225.sql` → `seed/base-ficticia/carga-0225.sql` (99706 bytes)
226. `entrega/seed/base-ficticia/carga-0226.sql` → `seed/base-ficticia/carga-0226.sql` (86615 bytes)
227. `entrega/seed/base-ficticia/carga-0227.sql` → `seed/base-ficticia/carga-0227.sql` (91069 bytes)
228. `entrega/seed/base-ficticia/carga-0228.sql` → `seed/base-ficticia/carga-0228.sql` (53564 bytes)
229. `entrega/seed/base-ficticia/carga-0229.sql` → `seed/base-ficticia/carga-0229.sql` (77767 bytes)
230. `entrega/seed/base-ficticia/carga-0230.sql` → `seed/base-ficticia/carga-0230.sql` (81828 bytes)
231. `entrega/seed/base-ficticia/carga-0231.sql` → `seed/base-ficticia/carga-0231.sql` (76983 bytes)
232. `entrega/seed/base-ficticia/carga-0232.sql` → `seed/base-ficticia/carga-0232.sql` (99914 bytes)
233. `entrega/seed/base-ficticia/carga-0233.sql` → `seed/base-ficticia/carga-0233.sql` (24376 bytes)
234. `entrega/seed/base-ficticia/carga-0234.sql` → `seed/base-ficticia/carga-0234.sql` (97205 bytes)
235. `entrega/seed/base-ficticia/carga-0235.sql` → `seed/base-ficticia/carga-0235.sql` (75697 bytes)
236. `entrega/seed/base-ficticia/carga-0236.sql` → `seed/base-ficticia/carga-0236.sql` (54436 bytes)
237. `entrega/seed/base-ficticia/carga-0237.sql` → `seed/base-ficticia/carga-0237.sql` (99706 bytes)
238. `entrega/seed/base-ficticia/carga-0238.sql` → `seed/base-ficticia/carga-0238.sql` (86615 bytes)
239. `entrega/seed/base-ficticia/carga-0239.sql` → `seed/base-ficticia/carga-0239.sql` (60396 bytes)
240. `entrega/seed/base-ficticia/carga-0240.sql` → `seed/base-ficticia/carga-0240.sql` (41178 bytes)
241. `entrega/seed/base-ficticia/carga-0241.sql` → `seed/base-ficticia/carga-0241.sql` (71264 bytes)
242. `entrega/seed/base-ficticia/carga-0242.sql` → `seed/base-ficticia/carga-0242.sql` (91766 bytes)
243. `entrega/seed/base-ficticia/carga-0243.sql` → `seed/base-ficticia/carga-0243.sql` (76088 bytes)
244. `entrega/seed/base-ficticia/carga-0244.sql` → `seed/base-ficticia/carga-0244.sql` (76903 bytes)
245. `entrega/seed/base-ficticia/carga-0245.sql` → `seed/base-ficticia/carga-0245.sql` (99829 bytes)
246. `entrega/seed/base-ficticia/carga-0246.sql` → `seed/base-ficticia/carga-0246.sql` (67631 bytes)
247. `entrega/seed/base-ficticia/carga-0247.sql` → `seed/base-ficticia/carga-0247.sql` (99789 bytes)
248. `entrega/seed/base-ficticia/carga-0248.sql` → `seed/base-ficticia/carga-0248.sql` (94913 bytes)
249. `entrega/seed/base-ficticia/carga-0249.sql` → `seed/base-ficticia/carga-0249.sql` (90247 bytes)
250. `entrega/seed/base-ficticia/carga-0250.sql` → `seed/base-ficticia/carga-0250.sql` (99706 bytes)
251. `entrega/seed/base-ficticia/carga-0251.sql` → `seed/base-ficticia/carga-0251.sql` (86616 bytes)
252. `entrega/seed/base-ficticia/carga-0252.sql` → `seed/base-ficticia/carga-0252.sql` (60396 bytes)
253. `entrega/seed/base-ficticia/carga-0253.sql` → `seed/base-ficticia/carga-0253.sql` (41178 bytes)
254. `entrega/seed/base-ficticia/carga-0254.sql` → `seed/base-ficticia/carga-0254.sql` (93825 bytes)
255. `entrega/seed/base-ficticia/carga-0255.sql` → `seed/base-ficticia/carga-0255.sql` (82403 bytes)
256. `entrega/seed/base-ficticia/carga-0256.sql` → `seed/base-ficticia/carga-0256.sql` (97862 bytes)
257. `entrega/seed/base-ficticia/carga-0257.sql` → `seed/base-ficticia/carga-0257.sql` (14582 bytes)
258. `entrega/seed/base-ficticia/carga-0258.sql` → `seed/base-ficticia/carga-0258.sql` (99742 bytes)
259. `entrega/seed/base-ficticia/carga-0259.sql` → `seed/base-ficticia/carga-0259.sql` (86979 bytes)
260. `entrega/seed/base-ficticia/carga-0260.sql` → `seed/base-ficticia/carga-0260.sql` (60396 bytes)
261. `entrega/seed/base-ficticia/carga-0261.sql` → `seed/base-ficticia/carga-0261.sql` (41178 bytes)
262. `entrega/seed/base-ficticia/carga-0262.sql` → `seed/base-ficticia/carga-0262.sql` (71264 bytes)
263. `entrega/seed/base-ficticia/carga-0263.sql` → `seed/base-ficticia/carga-0263.sql` (90066 bytes)
264. `entrega/seed/base-ficticia/carga-0264.sql` → `seed/base-ficticia/carga-0264.sql` (74408 bytes)
265. `entrega/seed/base-ficticia/carga-0265.sql` → `seed/base-ficticia/carga-0265.sql` (78439 bytes)
266. `entrega/seed/base-ficticia/carga-0266.sql` → `seed/base-ficticia/carga-0266.sql` (99865 bytes)
267. `entrega/seed/base-ficticia/carga-0267.sql` → `seed/base-ficticia/carga-0267.sql` (53051 bytes)
268. `entrega/seed/base-ficticia/carga-0268.sql` → `seed/base-ficticia/carga-0268.sql` (59364 bytes)
269. `entrega/seed/base-ficticia/carga-0269.sql` → `seed/base-ficticia/carga-0269.sql` (99789 bytes)
270. `entrega/seed/base-ficticia/carga-0270.sql` → `seed/base-ficticia/carga-0270.sql` (94913 bytes)
271. `entrega/seed/base-ficticia/carga-0271.sql` → `seed/base-ficticia/carga-0271.sql` (99928 bytes)
272. `entrega/seed/base-ficticia/carga-0272.sql` → `seed/base-ficticia/carga-0272.sql` (67163 bytes)
273. `entrega/seed/base-ficticia/carga-0273.sql` → `seed/base-ficticia/carga-0273.sql` (99854 bytes)
274. `entrega/seed/base-ficticia/carga-0274.sql` → `seed/base-ficticia/carga-0274.sql` (87617 bytes)
275. `entrega/seed/base-ficticia/carga-0275.sql` → `seed/base-ficticia/carga-0275.sql` (85969 bytes)
276. `entrega/seed/base-ficticia/carga-0276.sql` → `seed/base-ficticia/carga-0276.sql` (44714 bytes)
277. `entrega/seed/base-ficticia/carga-0277.sql` → `seed/base-ficticia/carga-0277.sql` (56466 bytes)
278. `entrega/seed/base-ficticia/carga-0278.sql` → `seed/base-ficticia/carga-0278.sql` (95918 bytes)
279. `entrega/seed/base-ficticia/carga-0279.sql` → `seed/base-ficticia/carga-0279.sql` (66598 bytes)
280. `entrega/seed/base-ficticia/carga-0280.sql` → `seed/base-ficticia/carga-0280.sql` (77033 bytes)
281. `entrega/seed/base-ficticia/carga-0281.sql` → `seed/base-ficticia/carga-0281.sql` (98664 bytes)
282. `entrega/seed/base-ficticia/carga-0282.sql` → `seed/base-ficticia/carga-0282.sql` (81055 bytes)
283. `entrega/seed/base-ficticia/carga-0283.sql` → `seed/base-ficticia/carga-0283.sql` (63147 bytes)
284. `entrega/seed/base-ficticia/carga-0284.sql` → `seed/base-ficticia/carga-0284.sql` (54647 bytes)
285. `entrega/seed/base-ficticia/carga-0285.sql` → `seed/base-ficticia/carga-0285.sql` (99800 bytes)
286. `entrega/seed/base-ficticia/carga-0286.sql` → `seed/base-ficticia/carga-0286.sql` (88171 bytes)
287. `entrega/seed/base-ficticia/carga-0287.sql` → `seed/base-ficticia/carga-0287.sql` (76519 bytes)
288. `entrega/seed/base-ficticia/carga-0288.sql` → `seed/base-ficticia/carga-0288.sql` (79523 bytes)
289. `entrega/seed/base-ficticia/carga-0289.sql` → `seed/base-ficticia/carga-0289.sql` (94544 bytes)
290. `entrega/seed/base-ficticia/carga-0290.sql` → `seed/base-ficticia/carga-0290.sql` (99742 bytes)
291. `entrega/seed/base-ficticia/carga-0291.sql` → `seed/base-ficticia/carga-0291.sql` (86979 bytes)
292. `entrega/seed/base-ficticia/carga-0292.sql` → `seed/base-ficticia/carga-0292.sql` (76025 bytes)
293. `entrega/seed/base-ficticia/carga-0293.sql` → `seed/base-ficticia/carga-0293.sql` (37228 bytes)
294. `entrega/seed/base-ficticia/carga-0294.sql` → `seed/base-ficticia/carga-0294.sql` (64214 bytes)
295. `entrega/seed/base-ficticia/carga-0295.sql` → `seed/base-ficticia/carga-0295.sql` (62928 bytes)
296. `entrega/seed/base-ficticia/carga-0296.sql` → `seed/base-ficticia/carga-0296.sql` (77001 bytes)
297. `entrega/seed/base-ficticia/carga-0297.sql` → `seed/base-ficticia/carga-0297.sql` (99922 bytes)
298. `entrega/seed/base-ficticia/carga-0298.sql` → `seed/base-ficticia/carga-0298.sql` (61059 bytes)
299. `entrega/seed/base-ficticia/carga-0299.sql` → `seed/base-ficticia/carga-0299.sql` (99742 bytes)
300. `entrega/seed/base-ficticia/carga-0300.sql` → `seed/base-ficticia/carga-0300.sql` (86979 bytes)
301. `entrega/seed/base-ficticia/carga-0301.sql` → `seed/base-ficticia/carga-0301.sql` (60396 bytes)
302. `entrega/seed/base-ficticia/carga-0302.sql` → `seed/base-ficticia/carga-0302.sql` (82607 bytes)
303. `entrega/seed/base-ficticia/carga-0303.sql` → `seed/base-ficticia/carga-0303.sql` (80825 bytes)
304. `entrega/seed/base-ficticia/carga-0304.sql` → `seed/base-ficticia/carga-0304.sql` (68646 bytes)
305. `entrega/seed/base-ficticia/carga-0305.sql` → `seed/base-ficticia/carga-0305.sql` (76914 bytes)
306. `entrega/seed/base-ficticia/carga-0306.sql` → `seed/base-ficticia/carga-0306.sql` (99851 bytes)
307. `entrega/seed/base-ficticia/carga-0307.sql` → `seed/base-ficticia/carga-0307.sql` (41670 bytes)

## Adendo de execução do pouso final — 09/11/2026 (PARCIAL: parada por HTTP 429)

Adendo lavrado pelo executor do pouso final (sessão distinta da coordenação
original), após a liberação do dono de 09/11/2026 01:40 −03 ("libera").
Nenhuma linha anterior deste LANDING foi alterada.

- **Pré-voo:** plano de espera conferido arquivo a arquivo — 262 lotes, 289
  arquivos, 21.855.777 bytes, maior lote 112.584 bytes; os 26 arquivos fora do
  plano são exatamente os byte-idênticos já na main (errata do complemento).
  Portão reexecutado sobre o estado final: exit 1 com o MESMO perfil de 314
  itens de `portao-saida-final.txt` (regras 1/3/6 — falsos positivos já
  demonstrados no `PORTAO-REGISTRO-BASE-FICTICIA-12K.md` §§2, 3 e 6.2; saída
  desta reexecução em `portao-saida-pouso.txt`). HEAD no ato: `6625ed76`
  (diário C8); diretório remoto `seed/base-ficticia/` com as 45 entradas
  esperadas — rebase verificado (caminhos exclusivos do pacote).
- **Execução:** runner de `push/espera/` (retomável, HEAD conferido após cada
  lote). Interrupção por morte do processo no lote 108 às ~04:57 UTC
  (reinício de runtime): apurado pela main que o lote 108 NÃO pousara
  (HEAD seguia `97cdd78`, lote 107; `carga-0140.sql` ausente no remoto) e o
  runner foi relançado do estado (done=107), sem repetir escrita de desfecho
  desconhecido.
- **Parada — HTTP 429 no lote 136** (`seed/base-ficticia/carga-0170.sql`),
  às 05:18:54 UTC: o runner parou na hora (exit 3), como manda a regra
  (429 = parada dura, sem insistir). **Estado provado localmente:**
  `push/espera/state.json` com done = lotes 1–135 contínuos; HEAD da main ao
  fim do lote 135 = `c8c0b88d0d516e38bfe6431d44729a29d7f57509`. Commits
  deste pouso: 135 (de `95c41ce`, lote 1, sobre `6625ed76`, até `c8c0b88`,
  lote 135).
- **Lote 136 — desfecho INDETERMINADO (não presumido):** o payload do erro
  429 trazia `refs/heads/main` apontando para o commit
  `983b13f829331f4f42998529e6f95b94cf1340b1` (≠ HEAD do lote 135), o que
  sugere que o commit do lote 136 pode ter sido criado antes de o limite
  estourar — mas NÃO foi verificado (a regra do 429 veda nova chamada ao
  provedor nesta tarefa). **Na retomada, ANTES de repulsar:** ler a main e
  confirmar se existe o commit "lote 136/262"; se existir, marcar 136 em
  `push/espera/state.json` (done + head) e só então relançar o runner.
- **Resta por pousar:** lotes 136–262 (127 lotes) + 1 lote de diário
  (verbete do pacote, sobre o blob vivo no ato) + verificação pós-pouso por
  hash (críticos + listagem integral 315) — tudo após o cooldown do limite
  do provedor, relançando o mesmo runner de `push/espera/`.

## Arquivos críticos para verificação pós-pouso (hash)

- `seed/base-ficticia/gerar-base-ficticia.py`
- `seed/base-ficticia/limpeza-base-ficticia.sql`
- `seed/base-ficticia/00-tenant-ficticio.sql`
- `seed/base-ficticia/carga-0001.sql` (primeiro carga)
- `seed/base-ficticia/carga-0307.sql` (último carga)
- `seed/base-ficticia/LEIA-ME.md`
- `seed/base-ficticia/RELATORIO-COBERTURA.md`
- `seed/base-ficticia/matriz.csv` e `seed/base-ficticia/manifesto.json` (SHA-256 das cargas conferem com o manifesto)

---

## ADENDO — 09/11/2026 (lote R7-B, correção do achado R7-I-m1, menor, confirmado)

Corretor do lote R7-B (sessão `b6cf0c91-9a27-46d5-a359-d4a9ddcaecb9`).
Nenhuma linha anterior deste LANDING foi alterada; este adendo apenas
supersede os números pré-complemento abaixo. O complemento EMASA
(`RELATORIO-COMPLEMENTO-EMASA.md`) é a fonte dos valores corretos.

**Valores pré-complemento que ficam supersededos neste LANDING:**

- `carga-0001.sql` com **62.708 bytes** (item 1 da lista de arquivos);
- `carga-0011.sql` com **61.154 bytes** (item 11);
- ausência de qualquer menção ao complemento, à espera e ao plano
  consolidado (262 lotes / 289 arquivos).

**Valores corretos pós-complemento:**

- **123.120 linhas** (COPY 121.270 + INSERT 1.850) e **23.540.541 bytes**
  de carga em 307 arquivos, maior arquivo **99.993 bytes**
  (`carga-0129.sql`);
- `carga-0001.sql`: **66.285 bytes**; `carga-0011.sql`: **61.106 bytes**;
- tenant **"Autarquia Ficticia Intermunicipal de Aguas — Porto das
  Gaivotas e Vale do Rio Claro (TESTE FICTICIO)"**.

**Fontes:** revisor 3 da Rodada 7 (`03-REVISOR-INTEGRACAO.md`, R7-I-m1)
e conferência mecânica própria do corretor sobre
`entrega/seed/base-ficticia/`: soma dos 307 `carga-*.sql` =
23.540.541 bytes; COPY 121.270 + INSERT 1.850 = 123.120;
`carga-0001.sql` = 66.285 bytes; `carga-0011.sql` = 61.106 bytes;
maior arquivo `carga-0129.sql` = 99.993 bytes.

**Nota de estatuto:** este LANDING é o rascunho original do pacote
(Lote 0 + Lotes 1..307 = 315 arquivos). O plano operativo do pouso
consolidado é `push/espera/plan.json` (262 lotes / 289 arquivos,
21.855.777 bytes, 0 faltando — conferido pelo revisor 3, §1(h)); os
26 arquivos fora do plano são a `limpeza-base-ficticia.sql` + 25 cargas
já no estado final. Este adendo não altera o plano nem o estado do
pouso em curso.

## Adendo — 09/11/2026 (lote R8-C, A-01 — coordenador do lote)

Nenhuma linha anterior deste LANDING foi alterada. A linha do adendo de
execução "Resta por pousar: lotes 136–262 (127 lotes)" fica
SUPERSEDED: o lote 136 POUSOU (commit
`983b13f829331f4f42998529e6f95b94cf1340b1`, 05:18:54Z) e o estado foi
regularizado pelo lote R8-C (A-01): `push/espera/state.json` marca
done 1–136, head `983b13f829331f4f42998529e6f95b94cf1340b1`
(SHA-256 do ficheiro `83b07cc7a727d2927b789d6545fa78d205fc669abd2da948a5ee5fdf765dec2f`),
com a actuação registada em `push/espera/exec.log` (07:31:20 UTC,
runner `push/espera/runner-big.py`, tecto implementado 400_000 bytes).
**Resta por pousar: lotes 137–262 (126 lotes)** + 1 lote de diário
(verbete do pacote, sobre o blob vivo no ato) + verificação pós-pouso
por hash — tudo após o cooldown do limite do provedor, relançando o
mesmo runner de `push/espera/`, que retoma no lote 137. O lote 136 NÃO
é repulsado.

## Adendo — 09/11/2026 ~08:35 UTC (coordenador do lote R8-C)

Nenhuma linha anterior deste LANDING foi alterada. A linha do adendo
anterior do lote R8-C "Resta por pousar: lotes 137–262 (126 lotes)"
fica SUPERSEDED: por ordem do dono às 05:09 −03 (08:10:05Z) a fila
retomou e os lotes 137–191 pousaram; nova PARADA no lote 192 por
HTTP 429 às 08:17:39Z (commit
`3156a0bf1b3c5b2874c721a04b4291d40309f050` criado ANTES do 429).
`push/espera/state.json` marca done 1–191 contíguo, head
`87be10a4134f71e2b3b9fb3793c9edf81453d757` (SHA-256 `7887317b…`).
**Resta por pousar: lotes 192–262 (71 lotes)** + 1 lote de diário +
verificação pós-pouso por hash. O lote 192 NÃO é repulsado (na
retomada, regularizar pelo rito do A-01: marcar done + head, com
linha em exec.log); o lote 136 continua não repulsado.

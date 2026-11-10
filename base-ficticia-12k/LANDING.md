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

---

## ADENDO — 09/11/2026 (lote R13-C, correção do achado R13-P2-01, menor, confirmado)

Produtor do lote R13-C (sessão `d063cd40-24f0-4f12-8d67-016204c316ff`).
Nenhuma linha anterior deste LANDING foi alterada; este adendo é uma
secção nova acrescentada no fim do ficheiro. O `exec.log` não foi
reescrito.

**Motivo (R13-P2-01, Rodada 13 da auditoria contínua, 09/11/2026):** o fecho
do pouso da base fictícia 12k — 315/315 ficheiros byte-idênticos na main +
verbete do diário (commit `3edcbf7…`) — vivia só na linha de prosa do
`push/espera/exec.log:596`, sem tabela hash a hash arquivada. Este adendo
arquiva essa tabela, gerada e conferida agora por leitura viva da main.

**Referências:**

- `push/espera/exec.log:596` — linha `FECHO DO POUSO` (15:24:54 UTC,
  09/11/2026): lotes 193–262 à 1.ª tentativa, sem HTTP 429, FIM 262/262,
  HEAD `fb9d346cdae444cdf7a2a266888bf294ea01233e`; verificação pós-pouso
  315/315; manifesto 308/308; diário commit
  `3edcbf7fec55eaa805fd47c9e76002442f609b78`, blob pós-pouso
  `e5c137e939d1b7432b87f478e1027709ffecdb99` (188.862 bytes).
- HEAD da main lido no acto desta verificação (leitura viva, conector
  GitHub, `list_commits` ref main):
  `91534571a90a835d71d7a01d5ccec10c7f4a29a4` (ED-51, 2026-11-09T16:41:20Z)
  — igual ao HEAD verificado pela mesa na Rodada 13. A base pousou antes
  (head `fb9d346cdae4`); os commits posteriores (R7-A/B/C, ED-51) não
  tocam `seed/base-ficticia/`.
- Listagem viva integral de `seed/base-ficticia/` na main (conector GitHub,
  `get_file_contents`, ref main): resposta integral em bruto arquivada em
  `correcoes-r13-c/listagem-seed-base-ficticia-raw.json` (315 entradas,
  todas `type: file`, sem truncagem).

**Método:** para cada um dos 315 ficheiros de
`entrega/seed/base-ficticia/`, recomputado localmente o SHA-1 de blob git
(`git hash-object`) + tamanho em bytes (`wc -c` / `os.path.getsize`) e
comparado um a um com a listagem viva (SHA-1 de blob + tamanho por
entrada). Script de comparação + log integral em
`correcoes-r13-c/comparar-base-ficticia.py` e
`correcoes-r13-c/comparacao-base-ficticia.log`.
Corroboração independente: `carga-0307.sql` na main tem SHA-1 de blob
`964a081f82f4d0c299209a39912fc3eab2dbd93e` — o mesmo valor que a mesa
registou no spot-check da Rodada 13 (§4).

**Resultado: 315/315 ficheiros byte-idênticos — 0 faltas, 0 sobras,
0 divergências.**

**Reconciliação das contagens (explícita):**

- Directório local: 315 ficheiros = 307 `carga-*.sql` + 8 não-carga
  (`00-tenant-ficticio.sql`, `LEIA-ME.md`, `RELATORIO-COBERTURA.md`,
  `gerar-base-ficticia.py`, `limpeza-base-ficticia.sql`, `manifesto.json`,
  `matriz.csv`, `prova-carga-limpeza.sh`).
- `manifesto.json` declara 308 entradas (`arquivos`: dict ficheiro →
  `{bytes, sha256}`) = 307 `carga-*.sql` + **`matriz.csv`.**
- **Correcção de hipótese do brief:** a 308.ª entrada do manifesto é a
  `matriz.csv` — e NÃO o `00-tenant-ficticio.sql`, como o brief sugeria
  como provável. Prova: `matriz.csv` está nas chaves de
  `manifesto.json['arquivos']`; `00-tenant-ficticio.sql` não está.
- 315 = 308 (entradas do manifesto) + 7 ficheiros fora do manifesto
  (`00-tenant-ficticio.sql`, `LEIA-ME.md`, `RELATORIO-COBERTURA.md`,
  `gerar-base-ficticia.py`, `limpeza-base-ficticia.sql`,
  `manifesto.json`, `prova-carga-limpeza.sh`).
- Sanidade (valores do Adendo R7-B): `carga-0001.sql` = 66.285 bytes;
  `carga-0011.sql` = 61.106 bytes; soma dos 307 `carga-*.sql` =
  23.540.541 bytes — todos conferem.

**Cruzamento SHA-256 contra o manifesto: 308/308** (SHA-256 e `bytes` de
cada uma das 308 entradas recomputados localmente, 0 divergências,
0 ficheiros em falta).

**Tabela completa — ficheiro × SHA-1 de blob git × bytes (315 linhas,
ordenada pelo caminho, ordem byte/ASCII):**

| Ficheiro | SHA-1 de blob | Bytes |
|---|---|---|:
| `seed/base-ficticia/00-tenant-ficticio.sql` | `91a7db86353019e7899b3e48e008658084e925eb` | 1197 |
| `seed/base-ficticia/LEIA-ME.md` | `35b5da45d36c66c3ffca5ff5016df0da6a27ff86` | 4958 |
| `seed/base-ficticia/RELATORIO-COBERTURA.md` | `08544273fb7413457e504af44901c4feca22430a` | 9194 |
| `seed/base-ficticia/carga-0001.sql` | `7261223538094f4aa2c26b395db33b8d2f71a689` | 66285 |
| `seed/base-ficticia/carga-0002.sql` | `0bdae1cd424770ed762dc9541591b04f17cfaa19` | 99910 |
| `seed/base-ficticia/carga-0003.sql` | `11ab9dc83b47d14639acbceefa389a5dd5b77c4c` | 85303 |
| `seed/base-ficticia/carga-0004.sql` | `442dcb44fdb4c0e92a853d7d04015cb0ca72d9af` | 60396 |
| `seed/base-ficticia/carga-0005.sql` | `59dcecd978b6874508349292ed716ae63b4db9df` | 41178 |
| `seed/base-ficticia/carga-0006.sql` | `7768664b8480562cee1423564fca76e90f1fb538` | 71264 |
| `seed/base-ficticia/carga-0007.sql` | `e1925fc27c0d945628953e14283234a3df4c0596` | 89666 |
| `seed/base-ficticia/carga-0008.sql` | `4ea353ea6f5a4265c22c0848ba5360a7b990cd6a` | 74382 |
| `seed/base-ficticia/carga-0009.sql` | `48843928c6d520514f727f4e93a5f9fd26ec14a7` | 76882 |
| `seed/base-ficticia/carga-0010.sql` | `d037ca448293077b4a1e6b3fbeb4aabdd223694f` | 99820 |
| `seed/base-ficticia/carga-0011.sql` | `2b669a0d915ee25ca3b35d51d6475bc17e163f1d` | 61106 |
| `seed/base-ficticia/carga-0012.sql` | `619c6f9a5354a94bbee8fc9bec51ef39c4427d1e` | 99923 |
| `seed/base-ficticia/carga-0013.sql` | `679315443c6c5c3a9a113965ffdf199880d2e9bb` | 85598 |
| `seed/base-ficticia/carga-0014.sql` | `8dd69ab3c3d5422ad04b1b4f5d113f62a4194587` | 60396 |
| `seed/base-ficticia/carga-0015.sql` | `a67b6cce937ca67cebd40586a93f5120805494c4` | 41178 |
| `seed/base-ficticia/carga-0016.sql` | `56403572469c1f00bbad250720c7ae0f1b3081b5` | 71264 |
| `seed/base-ficticia/carga-0017.sql` | `21a7d884f4db6338b47808906065d1d51aa6fff8` | 89666 |
| `seed/base-ficticia/carga-0018.sql` | `881e0622b115ceeeb4c105f226d684f309eaf701` | 74372 |
| `seed/base-ficticia/carga-0019.sql` | `71127da038949bb1be4130fff48820403424c16d` | 76873 |
| `seed/base-ficticia/carga-0020.sql` | `6bb5fe1cf852794bc7bc14c1535f51235d4e6429` | 99792 |
| `seed/base-ficticia/carga-0021.sql` | `a95ed886d20c70e54976ebf06fdf07ef52dd38b8` | 60948 |
| `seed/base-ficticia/carga-0022.sql` | `620f70c998ebaa18ee3ca3bb8b5302583e536f74` | 99765 |
| `seed/base-ficticia/carga-0023.sql` | `a14174fa1ae768e06cadaa34fa471a19555057f8` | 85957 |
| `seed/base-ficticia/carga-0024.sql` | `c0de3f82d97c5d29ea5092ff5e4d40beed1239e4` | 60396 |
| `seed/base-ficticia/carga-0025.sql` | `f01b8f4d7737bec61d4456af6ee1cdf668a38cd6` | 41178 |
| `seed/base-ficticia/carga-0026.sql` | `1d4fde9f9b2f71ed02f7aff955d032ee26df4779` | 71264 |
| `seed/base-ficticia/carga-0027.sql` | `42000ea4d3f68612ff1efdabf30b98c8a9e08639` | 89666 |
| `seed/base-ficticia/carga-0028.sql` | `7944b3d976501e49b29b1b5e1042ac986f48408b` | 74366 |
| `seed/base-ficticia/carga-0029.sql` | `ba6f5682ec4c020198229dba560a25d11e9900e0` | 76922 |
| `seed/base-ficticia/carga-0030.sql` | `9b2718f907e02b27e4af3897a3cccb826f757972` | 99854 |
| `seed/base-ficticia/carga-0031.sql` | `0877ad7fe8c123d8b4fca3fc20eedc1486f44eee` | 61051 |
| `seed/base-ficticia/carga-0032.sql` | `ed2264b00c0ccd87a95b735157257b1dce3eecaf` | 99778 |
| `seed/base-ficticia/carga-0033.sql` | `455f2cb9eaa3fcd65302837f3c4823c08d9b3388` | 85943 |
| `seed/base-ficticia/carga-0034.sql` | `7c8ac37829e7ccc215e2c312e8d14588e937adda` | 60396 |
| `seed/base-ficticia/carga-0035.sql` | `54a05aff48d7667ecf67d1b819ea498d68d740f9` | 41178 |
| `seed/base-ficticia/carga-0036.sql` | `36698314551519aae619209c0b595056af8a02d8` | 71264 |
| `seed/base-ficticia/carga-0037.sql` | `50fefd2d3f754021915e8b4e75200bc93cc62c71` | 89666 |
| `seed/base-ficticia/carga-0038.sql` | `38936e06fd32e6ea1a8eb47512474e1475da4913` | 74356 |
| `seed/base-ficticia/carga-0039.sql` | `ad61c4e343e2a3c0a7f91d4af241b8c88f104499` | 76863 |
| `seed/base-ficticia/carga-0040.sql` | `9568d02703589d9a5ad20458008ca60ad2049162` | 99784 |
| `seed/base-ficticia/carga-0041.sql` | `7e328e89dfc1b2fab44c79600648055e3e957860` | 61089 |
| `seed/base-ficticia/carga-0042.sql` | `8e5f02bffa1c9fc3e5de8a47df628384056e2807` | 99964 |
| `seed/base-ficticia/carga-0043.sql` | `4c9871bf28589501c747579a3a9b0e5c10260db8` | 85957 |
| `seed/base-ficticia/carga-0044.sql` | `98919d727ffe45b2dcbed599b61d1379f8461d59` | 60396 |
| `seed/base-ficticia/carga-0045.sql` | `7be7d110f77b01c9d45de74c48818bef7d20d0ba` | 41178 |
| `seed/base-ficticia/carga-0046.sql` | `00117357d069069b9b19832fefe3a6627029d7cb` | 71264 |
| `seed/base-ficticia/carga-0047.sql` | `19899cf35f26e618333a7b66876ccae2cfb7c8eb` | 89666 |
| `seed/base-ficticia/carga-0048.sql` | `b2f4451a5b7eef6fd5308b12dbe65e99284541c9` | 74370 |
| `seed/base-ficticia/carga-0049.sql` | `d5b407a5912d2e4fd667355421ce7f7b4271b8d6` | 76500 |
| `seed/base-ficticia/carga-0050.sql` | `366f4e63588530d3554ff243204b8983fd42fcf8` | 99825 |
| `seed/base-ficticia/carga-0051.sql` | `9042e63a619e8424de80e6fd8080dc41dd47ce75` | 60942 |
| `seed/base-ficticia/carga-0052.sql` | `ac769ad38e8ee6564eeb91c212297badabd65386` | 99964 |
| `seed/base-ficticia/carga-0053.sql` | `41d8decaadbd6f3573a60d0ef4f293a6e9c4ce98` | 85957 |
| `seed/base-ficticia/carga-0054.sql` | `bdc1ae9967c2ae57f4fa6bcfd0de9c7ae882ac5f` | 60396 |
| `seed/base-ficticia/carga-0055.sql` | `019492173f5ae77675755bcfb603c1bd52569f0c` | 41178 |
| `seed/base-ficticia/carga-0056.sql` | `c0f226105b1b6d7986af921978d337af6956b05d` | 71264 |
| `seed/base-ficticia/carga-0057.sql` | `d9c105dcb95be93a004b8407f18ffb8504aaa879` | 89666 |
| `seed/base-ficticia/carga-0058.sql` | `54eb006d5ada757fbcb804a3d7776ab00eea1822` | 74372 |
| `seed/base-ficticia/carga-0059.sql` | `31cfc68638ece9f439ed9fef3e15dcfa9d52bb10` | 76093 |
| `seed/base-ficticia/carga-0060.sql` | `31600fb8bceef1647ade1360c451fc283835ad39` | 99824 |
| `seed/base-ficticia/carga-0061.sql` | `0018e14e18413ec1399cac9faa1d18196336f8c7` | 61145 |
| `seed/base-ficticia/carga-0062.sql` | `5bd4d9ab4cdf205590ba5478f6e60aee565e4ede` | 99953 |
| `seed/base-ficticia/carga-0063.sql` | `844cda6ebc1a0bdd3f6310a41107f106ed13824d` | 86318 |
| `seed/base-ficticia/carga-0064.sql` | `c460fbf3f22411ed0d40d0b916b74269258ad2c7` | 60396 |
| `seed/base-ficticia/carga-0065.sql` | `dc3cb1cda3ace5f920fc1d704f2a5e9b8ce390a6` | 41178 |
| `seed/base-ficticia/carga-0066.sql` | `8769179c501f7a81405211774f7fbdd9216f9870` | 71264 |
| `seed/base-ficticia/carga-0067.sql` | `5369828f4c419501addfe25dcdfc5edad94ef2e6` | 89666 |
| `seed/base-ficticia/carga-0068.sql` | `2d9f406a1a2e10cc6ef4f3d0ad94dcebf72584b4` | 74352 |
| `seed/base-ficticia/carga-0069.sql` | `a2f2aa26a26b4515c8253a270adf323de207c96a` | 76093 |
| `seed/base-ficticia/carga-0070.sql` | `bdb04758168a9657af492fc26bb5fa1d5a53b435` | 99828 |
| `seed/base-ficticia/carga-0071.sql` | `f6e0cab89c546af00be57706d6e6853a4649e069` | 61071 |
| `seed/base-ficticia/carga-0072.sql` | `1955aa9a98c19146623f32712aa7572e5c2c2b2c` | 99793 |
| `seed/base-ficticia/carga-0073.sql` | `b02a94a0196f1f2c4911f4358d8d2c7b395b1662` | 86253 |
| `seed/base-ficticia/carga-0074.sql` | `a8c5816eeebea500950e8880c640567ea78e03d8` | 60396 |
| `seed/base-ficticia/carga-0075.sql` | `9a94ef72b97ee6b7a12732e9e0a2e041dd206a6f` | 41178 |
| `seed/base-ficticia/carga-0076.sql` | `8cc9ad6d0dcffb2b7a4456a78b15ef4ce7aa779a` | 71264 |
| `seed/base-ficticia/carga-0077.sql` | `52853d9f7f114d9f50bd5411a5db7253df99534e` | 89666 |
| `seed/base-ficticia/carga-0078.sql` | `acb62908571ec11eeca650a9523c5403d7bddd11` | 74394 |
| `seed/base-ficticia/carga-0079.sql` | `27343c57c874181030389e40059b1671aadd8aa3` | 76110 |
| `seed/base-ficticia/carga-0080.sql` | `a1dc516d44668aaf9b3689c72cafb648251cd371` | 99842 |
| `seed/base-ficticia/carga-0081.sql` | `2f538d0a217c8544d5a1afc2f9155440cc82ad64` | 61127 |
| `seed/base-ficticia/carga-0082.sql` | `fb104a73e80b42a07ad9ce9a1f84845e961a696b` | 99903 |
| `seed/base-ficticia/carga-0083.sql` | `01debe96d578665fb0de81bead259db60058620c` | 86318 |
| `seed/base-ficticia/carga-0084.sql` | `c2bdf602b8a360eeaa50f9ae2c630bf0f210ead0` | 60396 |
| `seed/base-ficticia/carga-0085.sql` | `fb8b2331644e14734d5b709c6fb3c882ca3d6776` | 41178 |
| `seed/base-ficticia/carga-0086.sql` | `42f42c6f54579343d7553a2c227209ac2d6ef79c` | 71264 |
| `seed/base-ficticia/carga-0087.sql` | `689e4ea9d779b3880ba315f55fab07c89b4f3e18` | 89666 |
| `seed/base-ficticia/carga-0088.sql` | `770a3a28a63a5367354d99b23815c0c54b48329d` | 74388 |
| `seed/base-ficticia/carga-0089.sql` | `01db2bfc675cd7169190d4b3a9d8ba2ea50fee1a` | 76685 |
| `seed/base-ficticia/carga-0090.sql` | `5e77686d1dc7b3f7a1dd2566f64af1a02dd63534` | 99820 |
| `seed/base-ficticia/carga-0091.sql` | `59da66112cae6a3bc2949a7adbf8c6c87d1bf3fd` | 99037 |
| `seed/base-ficticia/carga-0092.sql` | `03cc07ed62cba7bbe72c4adf57693753ebc3b6c0` | 14254 |
| `seed/base-ficticia/carga-0093.sql` | `fae2a18ccfabbc44e07cc3be122fbede63ef2ff3` | 99706 |
| `seed/base-ficticia/carga-0094.sql` | `8ab19775bebd75b4dc45cb7ae8428dff19b1144a` | 86590 |
| `seed/base-ficticia/carga-0095.sql` | `c56ea76eb3bcae21594f9a08f9aac831ad24eaea` | 60396 |
| `seed/base-ficticia/carga-0096.sql` | `d903bf699f3f6bfda73838ca6f9cd1c6963c58fc` | 41178 |
| `seed/base-ficticia/carga-0097.sql` | `ebe761a125b3d1126607a64697650047049568a3` | 71264 |
| `seed/base-ficticia/carga-0098.sql` | `6bbac3b5d1733f7c4055a9254ab6c5670dd5850b` | 89666 |
| `seed/base-ficticia/carga-0099.sql` | `17d4549c9f0ed66ad7cf9f7255dd52068e3bac3f` | 74378 |
| `seed/base-ficticia/carga-0100.sql` | `582473e508c8996e854bd17c345198f4460b76ee` | 76880 |
| `seed/base-ficticia/carga-0101.sql` | `8b16c3c9e9ecd8e0aae352ba1f3a133a5d988fbb` | 99807 |
| `seed/base-ficticia/carga-0102.sql` | `e28c8782760ebb942e413a1749e7eeaa048ce7ef` | 76280 |
| `seed/base-ficticia/carga-0103.sql` | `d0dd689dce28254b0a462538701f81f93bce8ebd` | 54499 |
| `seed/base-ficticia/carga-0104.sql` | `3e6c4eea097deca7955c60e1ce18655dd92ace62` | 99803 |
| `seed/base-ficticia/carga-0105.sql` | `d0d266d9bf9ee87062601e793fe095b451860d41` | 86318 |
| `seed/base-ficticia/carga-0106.sql` | `c63c724be6ee305bfd96b950bfd3575b006b1918` | 60396 |
| `seed/base-ficticia/carga-0107.sql` | `712bb21797f5bf380ed9de41a9f42f02cf1c6d97` | 41178 |
| `seed/base-ficticia/carga-0108.sql` | `4ad9c96f943528a218c4c00f6f2af23ede911554` | 71264 |
| `seed/base-ficticia/carga-0109.sql` | `64928acffbdac77d202147c549ab82ba932de207` | 89666 |
| `seed/base-ficticia/carga-0110.sql` | `2046adafccf6d50ebb63c59f78bd522743fe7bbb` | 74376 |
| `seed/base-ficticia/carga-0111.sql` | `cad9931fa76deeb11798c9a50743856c48b9604f` | 76883 |
| `seed/base-ficticia/carga-0112.sql` | `e5b1e94d405406a85eaa995993c93dff5b488cf9` | 99811 |
| `seed/base-ficticia/carga-0113.sql` | `c11a71f93aaf10f7d25b62726494fbe6bbcc56b4` | 76279 |
| `seed/base-ficticia/carga-0114.sql` | `9f03d899ea3c9400c99151405ddaa088a5f875e3` | 97397 |
| `seed/base-ficticia/carga-0115.sql` | `db2acbb317f0c4cf99c3a563baa3aca4fc879ccd` | 72315 |
| `seed/base-ficticia/carga-0116.sql` | `23a0e35093449fc6013d9482ba4b712b6b1c8744` | 99903 |
| `seed/base-ficticia/carga-0117.sql` | `0dd1f28ad197d1a02d8cfd599c7f67ec666f3d1e` | 86318 |
| `seed/base-ficticia/carga-0118.sql` | `5c8a6337bb73aa7cfead6641d954a88e3dbc27f9` | 60396 |
| `seed/base-ficticia/carga-0119.sql` | `cc28640171a6353fb512e87ebe6316c20eedea8b` | 62134 |
| `seed/base-ficticia/carga-0120.sql` | `ea18310e80d69c84368f17e9188a97aaedf71f01` | 41178 |
| `seed/base-ficticia/carga-0121.sql` | `aa4c5db4e2df3a6d1ee906e29a28e5b73864ad47` | 71264 |
| `seed/base-ficticia/carga-0122.sql` | `914e9f131362f33afd5ea10b922c20ff1162c0b7` | 89666 |
| `seed/base-ficticia/carga-0123.sql` | `c75718c447505cc74a392997b01729bd1525dd19` | 74372 |
| `seed/base-ficticia/carga-0124.sql` | `eab65e52583aac45d47555ab2e416f626cd2f6a6` | 76885 |
| `seed/base-ficticia/carga-0125.sql` | `58007017aee1b9026b312bafb26ff0559210241c` | 99809 |
| `seed/base-ficticia/carga-0126.sql` | `e9f7fee88e1e48b4c5815480087036467621b323` | 41683 |
| `seed/base-ficticia/carga-0127.sql` | `a3b85b7c67c1c19cd70180467fef9d34eeb13231` | 97397 |
| `seed/base-ficticia/carga-0128.sql` | `61fedecee54586c4b29ee5e7ee8d8a3295762552` | 72319 |
| `seed/base-ficticia/carga-0129.sql` | `c058d1a3ee1c024596362123f118d38499c2f876` | 99993 |
| `seed/base-ficticia/carga-0130.sql` | `32fdd28845d1082f14b4e51751823a9423432410` | 86253 |
| `seed/base-ficticia/carga-0131.sql` | `719399b847453920fada09ddf093a8978bce18e3` | 60396 |
| `seed/base-ficticia/carga-0132.sql` | `c1aac18b546c7998bedbc1cda619267b39e350dd` | 99710 |
| `seed/base-ficticia/carga-0133.sql` | `f3bc9b633adf673a3e08493a17c40449ec7370fc` | 65431 |
| `seed/base-ficticia/carga-0134.sql` | `00ea77e3a23c28297083e4ffafaf1b0f37ae5c6c` | 71264 |
| `seed/base-ficticia/carga-0135.sql` | `8576e95eff06aff9ad0c82877d3cfe0bc0b52b64` | 89666 |
| `seed/base-ficticia/carga-0136.sql` | `9a54d09d4154d16579cd19a80ff59b52abeea931` | 74376 |
| `seed/base-ficticia/carga-0137.sql` | `6729f3ece05d1654379aa8a3e8ef00991382fc7e` | 76895 |
| `seed/base-ficticia/carga-0138.sql` | `1f63a953ec983c841b17d7c4430d6a6b18cf4e7b` | 99825 |
| `seed/base-ficticia/carga-0139.sql` | `2e02215151a8bb2af17e4efc6ffa998a24ba0be0` | 60908 |
| `seed/base-ficticia/carga-0140.sql` | `d0a1f2a33d85aa004e3efd72af7ff3e446782add` | 99893 |
| `seed/base-ficticia/carga-0141.sql` | `f525cc9a0fafceffda272d58a1a66270fb8beea2` | 86253 |
| `seed/base-ficticia/carga-0142.sql` | `05dcd943bfbf3d061fa335d75c723ce2f331cb08` | 60396 |
| `seed/base-ficticia/carga-0143.sql` | `1f4e41eb484a2cada937cead19e3c8e1d7d6d03b` | 99710 |
| `seed/base-ficticia/carga-0144.sql` | `41e06d6ebad9b9fd7cd77adc2d99376e9644ecc4` | 65431 |
| `seed/base-ficticia/carga-0145.sql` | `65bb003f03b288ca40ac87ed394026092ca1dabe` | 71264 |
| `seed/base-ficticia/carga-0146.sql` | `5057e363ec66531042630233352cd796a409376d` | 89666 |
| `seed/base-ficticia/carga-0147.sql` | `33a2a80ebf4e66835e494e26e817bbe692a69327` | 74392 |
| `seed/base-ficticia/carga-0148.sql` | `95eaff7a0d53966c4217cbf8858f56daba7a2643` | 76917 |
| `seed/base-ficticia/carga-0149.sql` | `6206e454f0203b6ae0933df519faf711c5d817f1` | 99851 |
| `seed/base-ficticia/carga-0150.sql` | `0bb94cb1fb2107d22359b66ab51124e4c791e0bb` | 99038 |
| `seed/base-ficticia/carga-0151.sql` | `fc491717953bc3afc3f8411657340fe64a3945fc` | 14467 |
| `seed/base-ficticia/carga-0152.sql` | `d5c284db98d09184db1b5f7fedfbe4fa4c00c8f0` | 99706 |
| `seed/base-ficticia/carga-0153.sql` | `503848bbe52be8a7602c99e08f6353640246cdc8` | 86615 |
| `seed/base-ficticia/carga-0154.sql` | `e407f07a0d1e769addb7c7193f07a1f89b4c49ee` | 60396 |
| `seed/base-ficticia/carga-0155.sql` | `4ab177d5bcb1f6cc7cef1165b44b6f87d6d5a184` | 41178 |
| `seed/base-ficticia/carga-0156.sql` | `1d8298804ed62f8aa13beac5664d9a322c7c21dc` | 71264 |
| `seed/base-ficticia/carga-0157.sql` | `234b64ea2a56574202432c81aa92e48c0d975af4` | 89666 |
| `seed/base-ficticia/carga-0158.sql` | `8b234b1e2da1757ea7d7928d86da2f1c00b52727` | 74374 |
| `seed/base-ficticia/carga-0159.sql` | `13f24135c10ff8b4754b5915a09c629c902cbc3a` | 76898 |
| `seed/base-ficticia/carga-0160.sql` | `e7c23038b469f3ef93c95a0e4c2b95a916f55e51` | 99832 |
| `seed/base-ficticia/carga-0161.sql` | `db00a5e41d3638eeadbddedd5637671ac400203f` | 60900 |
| `seed/base-ficticia/carga-0162.sql` | `1d4fbd4724116fa691f494e8969acb9b8bd53b2f` | 99868 |
| `seed/base-ficticia/carga-0163.sql` | `9d357c5bc6742305793f59ca6d42f0b1b23262c1` | 86253 |
| `seed/base-ficticia/carga-0164.sql` | `ac2ac1a49fc2069f33207551a03edb8238481304` | 60396 |
| `seed/base-ficticia/carga-0165.sql` | `b5de75002189fefc9810056ef7bc0ded5977bc8c` | 41178 |
| `seed/base-ficticia/carga-0166.sql` | `96714f7f504612ee597600fa0069244394c75c87` | 71264 |
| `seed/base-ficticia/carga-0167.sql` | `5232fe0fab25ad9799a012c13936e60d81fec0aa` | 89666 |
| `seed/base-ficticia/carga-0168.sql` | `9e2f6e86285d5e0214be8dbd7a1e49ce4f24fdfe` | 74352 |
| `seed/base-ficticia/carga-0169.sql` | `01f5e05ae5c62e18838f65572553d936eab495dc` | 76877 |
| `seed/base-ficticia/carga-0170.sql` | `9be19a0fba957753070673ab6e78173d05675e3a` | 99795 |
| `seed/base-ficticia/carga-0171.sql` | `6435d44b95a8485b41e461c0349c675de5d2515c` | 61158 |
| `seed/base-ficticia/carga-0172.sql` | `a6177dd7d48e453fd1a55e35a97bbbc5dd76477e` | 99706 |
| `seed/base-ficticia/carga-0173.sql` | `c37fdf1502c9b0418b07c5cd8ac8d76bf89f45f6` | 86615 |
| `seed/base-ficticia/carga-0174.sql` | `77621928d7198aac3eb9c9be654698969bcefe4c` | 60396 |
| `seed/base-ficticia/carga-0175.sql` | `ffb79ab1c4278e0c5a214067098a4a13cb60af71` | 41178 |
| `seed/base-ficticia/carga-0176.sql` | `e0d4f42b378fe4b87ce829e91630fbe49d6b6d03` | 71264 |
| `seed/base-ficticia/carga-0177.sql` | `6f1affab643cdd5202acc4ea2797fc5206648171` | 89666 |
| `seed/base-ficticia/carga-0178.sql` | `43d1639be8208490f19e00b57fa90917c23fdc1d` | 74400 |
| `seed/base-ficticia/carga-0179.sql` | `775312c5e63d14ee1cb9dac40a59468d8ed82e2a` | 76095 |
| `seed/base-ficticia/carga-0180.sql` | `25fdde61ce0179c324e7883d02592b92f5bcff67` | 99812 |
| `seed/base-ficticia/carga-0181.sql` | `70e03cdb8b3b7dec11e1cf6271c947c5a7aeb2f0` | 61134 |
| `seed/base-ficticia/carga-0182.sql` | `37dbacb9d932e3043f9d2401c3872f08d4cea5fc` | 99853 |
| `seed/base-ficticia/carga-0183.sql` | `7066035a0ce112bee5abe432ada9199076e4eb9d` | 86318 |
| `seed/base-ficticia/carga-0184.sql` | `4ce697e8628d27a6b0829fe4a4156f57b7d5b409` | 60396 |
| `seed/base-ficticia/carga-0185.sql` | `f14cdac0419d98d7c0020a8cb08ca5a568c22adc` | 41178 |
| `seed/base-ficticia/carga-0186.sql` | `d16e836326c473b08118b4f0eca4d83aeb2f062f` | 71264 |
| `seed/base-ficticia/carga-0187.sql` | `be2e17ecd01e88788ca631ecef194466c1864fcd` | 89666 |
| `seed/base-ficticia/carga-0188.sql` | `c3c26c57107e00c8513842e01a711d330a1692f1` | 74400 |
| `seed/base-ficticia/carga-0189.sql` | `c0d6027cbc3377b0ffd0644c608aa275bfff1ae1` | 76541 |
| `seed/base-ficticia/carga-0190.sql` | `18cb3ca85080e94b9fba3cdfdf5f9e1f8cec961e` | 99876 |
| `seed/base-ficticia/carga-0191.sql` | `9a4b1824e3eceace40b987bd7abe9b7013c99bbb` | 61120 |
| `seed/base-ficticia/carga-0192.sql` | `e55f717dd2735bfefc96f89217477139e01cd58c` | 99878 |
| `seed/base-ficticia/carga-0193.sql` | `46d8d4b5b0bd57e1894ef242410b302b1675ce1f` | 86318 |
| `seed/base-ficticia/carga-0194.sql` | `f2fd62abd86780a9febbe32b5c87e6d107c21eb9` | 60396 |
| `seed/base-ficticia/carga-0195.sql` | `709fc3656a7e64530b853046677273a5ae023aef` | 41178 |
| `seed/base-ficticia/carga-0196.sql` | `497d940f4312df1f98772c257193a1380697a85a` | 71264 |
| `seed/base-ficticia/carga-0197.sql` | `15c0719530bc85586fbcf7533abf93d2dad3ae70` | 67366 |
| `seed/base-ficticia/carga-0198.sql` | `fadad178344947bce3463465b7d67c11e04dc3f6` | 71520 |
| `seed/base-ficticia/carga-0199.sql` | `c05b23e964110d511f95c5104e721ae0d37cb4a9` | 76889 |
| `seed/base-ficticia/carga-0200.sql` | `2408dbe4a141d751ecf9efb46fcfc101bc1691d2` | 99814 |
| `seed/base-ficticia/carga-0201.sql` | `642631ef556a0ca153c2e7b98b02053bb40d25e6` | 60922 |
| `seed/base-ficticia/carga-0202.sql` | `ef03e79f1f36c66f287c27636ef452eed9af0da7` | 99943 |
| `seed/base-ficticia/carga-0203.sql` | `df9d363e2965174e80dda77cf5cfe4309855d691` | 86253 |
| `seed/base-ficticia/carga-0204.sql` | `d37caff345904082ef4dbe19942a4dcc7e18df2a` | 60396 |
| `seed/base-ficticia/carga-0205.sql` | `470059f2f95c1f3e7ce7d332db811b8b37007784` | 41178 |
| `seed/base-ficticia/carga-0206.sql` | `4e33a387a8970feae3d3d20e79324d3500fcd1ce` | 71264 |
| `seed/base-ficticia/carga-0207.sql` | `3534eec7987afca3f824cca07dc0a94cc6bcff46` | 62928 |
| `seed/base-ficticia/carga-0208.sql` | `9ae20b33ca4bcf73e194ec2f66ff4736fad79b1e` | 76977 |
| `seed/base-ficticia/carga-0209.sql` | `c04619f902bda272f616da4dc3a8c38f8b85c5d2` | 99892 |
| `seed/base-ficticia/carga-0210.sql` | `ed8fa7cb73d927c286942dd2c31e12b3284e9fff` | 61228 |
| `seed/base-ficticia/carga-0211.sql` | `d89942f292033cf2f284b66e45ab7fd086af3b5d` | 99706 |
| `seed/base-ficticia/carga-0212.sql` | `8f9b1f52f8329bdb28487d76f7cd4480b8cc292a` | 86615 |
| `seed/base-ficticia/carga-0213.sql` | `110c371f10f3f2654a8c08f68897e226498ab6af` | 60396 |
| `seed/base-ficticia/carga-0214.sql` | `d3ed4d1297d382ea8a545b7d1fa49c9b3522127c` | 62928 |
| `seed/base-ficticia/carga-0215.sql` | `4c4b7d1ed3d785d62e5de5c88d4d33c2d9707a78` | 76959 |
| `seed/base-ficticia/carga-0216.sql` | `9c9dc978ad4f4c018e5d33c7756b8f71c2aa2d7b` | 99882 |
| `seed/base-ficticia/carga-0217.sql` | `f0c2d907e2c306cb1e3b750990068f8bb242d340` | 61170 |
| `seed/base-ficticia/carga-0218.sql` | `21326bb52f4771abe7cfe8ed5eec11804e1c2593` | 99706 |
| `seed/base-ficticia/carga-0219.sql` | `b1009e49ffda2a07f2b1d5a5c0275d2f6270ab95` | 86615 |
| `seed/base-ficticia/carga-0220.sql` | `fcc623359ed75395787e65bcc25cd785222d6a38` | 60396 |
| `seed/base-ficticia/carga-0221.sql` | `061a2dc1800019ecbe973b8357965d2938adfa76` | 62928 |
| `seed/base-ficticia/carga-0222.sql` | `5c0d6f2feed0e38db7dda511aa812cd7f899d6c2` | 76961 |
| `seed/base-ficticia/carga-0223.sql` | `0b8dec40f387bef8bed7416570e94c673a8c7efd` | 99886 |
| `seed/base-ficticia/carga-0224.sql` | `5a292ed54704ba73774b0e2f8773a953a800815f` | 95938 |
| `seed/base-ficticia/carga-0225.sql` | `74a4e9e75aeb84fa8cd8a7bbb3f343326670e8c2` | 99706 |
| `seed/base-ficticia/carga-0226.sql` | `79c2896eb66db1875975288294b8cd41093d76da` | 86615 |
| `seed/base-ficticia/carga-0227.sql` | `4a5c8784b38c3aa013f352b3ba3c041bbf7d03b2` | 91069 |
| `seed/base-ficticia/carga-0228.sql` | `aa9acf44a0226002c4634ca409c92a6967c969f8` | 53564 |
| `seed/base-ficticia/carga-0229.sql` | `70bfc8deb9bb7616411a20d96e3f061b2002292d` | 77767 |
| `seed/base-ficticia/carga-0230.sql` | `63476745c4d30c4d93ff67b40d099e6e0db798db` | 81828 |
| `seed/base-ficticia/carga-0231.sql` | `efbeb55256093af818c619759508624de9791213` | 76983 |
| `seed/base-ficticia/carga-0232.sql` | `7030221af68757db060f6d8c8aa4285a6ccb2a45` | 99914 |
| `seed/base-ficticia/carga-0233.sql` | `357637a12e8994aaa3f6375d8df6b4003cc4897d` | 24376 |
| `seed/base-ficticia/carga-0234.sql` | `6a33fcaf1eaf2294dafd798a2cef8779ce3652ef` | 97205 |
| `seed/base-ficticia/carga-0235.sql` | `647b74c545c91df8b79cab416aab45ea7e0b04c0` | 75697 |
| `seed/base-ficticia/carga-0236.sql` | `908a776c2ff498f2dd1edebfb1a2b295f4e979a0` | 54369 |
| `seed/base-ficticia/carga-0237.sql` | `16a9eeb11bd84894dbb7675f7d69f0e6ca802002` | 99706 |
| `seed/base-ficticia/carga-0238.sql` | `b948a4ca42588a327a97d3cf4dc127bb1018db36` | 86615 |
| `seed/base-ficticia/carga-0239.sql` | `31c1e0b6823d493a9b4c50c7ed1764f7af310c88` | 60396 |
| `seed/base-ficticia/carga-0240.sql` | `9c3bb84b55ac29c9d94df46b0e776a38489c462d` | 41178 |
| `seed/base-ficticia/carga-0241.sql` | `87c78005e7451c7885663ef03bb926cbb55a6bad` | 71264 |
| `seed/base-ficticia/carga-0242.sql` | `1af790edfcdd3035f2383bac84cf366094bfa5e1` | 91766 |
| `seed/base-ficticia/carga-0243.sql` | `f980eed542eddbf3199995d555be2a5924bf0a98` | 76088 |
| `seed/base-ficticia/carga-0244.sql` | `2f433e8004847c65454c6380eef078f9f4432aa6` | 76903 |
| `seed/base-ficticia/carga-0245.sql` | `057f86d4e02d341b80f8b8773c9b301c87f25f57` | 99829 |
| `seed/base-ficticia/carga-0246.sql` | `3dfe6e25de1010af26303e34d7792f9046d90909` | 67631 |
| `seed/base-ficticia/carga-0247.sql` | `c15da436148ba6913f113b96fdf9db12c4751ec3` | 99789 |
| `seed/base-ficticia/carga-0248.sql` | `943252939bf31bcf8a7a446e919a2eb1e366ceaa` | 94913 |
| `seed/base-ficticia/carga-0249.sql` | `7e813ec4ef16873b6afaa74526f48a54b90d5a84` | 89931 |
| `seed/base-ficticia/carga-0250.sql` | `2a2d8d30e432b56d955263917320b56f4dc5006a` | 99706 |
| `seed/base-ficticia/carga-0251.sql` | `d824e2e0ad37fbd727c1dc2a166e27185c70b142` | 86616 |
| `seed/base-ficticia/carga-0252.sql` | `69aa925e3d6e914638c41c2f5c146a3aa1ad488b` | 60396 |
| `seed/base-ficticia/carga-0253.sql` | `0ba800e1c16df55619fb2b52572e87ac09dd5d70` | 41178 |
| `seed/base-ficticia/carga-0254.sql` | `ba350512abbe5e5f39b804b6674d6ac220d531ef` | 93825 |
| `seed/base-ficticia/carga-0255.sql` | `c2e6b096708a933950b0afe725bb250f3e442267` | 82403 |
| `seed/base-ficticia/carga-0256.sql` | `56aab1b15a81a0d2a892d910b80b3defbce74999` | 97862 |
| `seed/base-ficticia/carga-0257.sql` | `11b56fb1de7d6fc7ad5d2a03ad3df3536860175f` | 14722 |
| `seed/base-ficticia/carga-0258.sql` | `7b9098b2f004d8e0973657f08f919e80f8b571d2` | 99742 |
| `seed/base-ficticia/carga-0259.sql` | `679f2c85041b1c653c34a2339e76c32341634fe3` | 86979 |
| `seed/base-ficticia/carga-0260.sql` | `d0ab3dedd254677d1c3c1a9226043a533a82e419` | 60396 |
| `seed/base-ficticia/carga-0261.sql` | `7614ea7dd7c16c0251843b834f6461dd3ff663f7` | 41178 |
| `seed/base-ficticia/carga-0262.sql` | `335f68bd085930d1e82766c3ff19374176a46d7c` | 71264 |
| `seed/base-ficticia/carga-0263.sql` | `e7f75aaca2392a7b339d97da9b36e454c8f94aa0` | 90066 |
| `seed/base-ficticia/carga-0264.sql` | `8c8a8e565957504cd76819d6d1c32f625aa45753` | 74408 |
| `seed/base-ficticia/carga-0265.sql` | `3903a5d1fbad1bb018d9d06f899e0c7c707b71f8` | 78439 |
| `seed/base-ficticia/carga-0266.sql` | `a4c9dd1b799179df3c3cbae3ac1da025c00680d2` | 99865 |
| `seed/base-ficticia/carga-0267.sql` | `a6fe2f26da5661ffdf7a38863edb916ded5c793c` | 53051 |
| `seed/base-ficticia/carga-0268.sql` | `314b855402b1e5ff3719fcc5401dc7e528284892` | 59364 |
| `seed/base-ficticia/carga-0269.sql` | `a01e217f8fec5754ef05f02ecdb0e78d0a52aacd` | 99789 |
| `seed/base-ficticia/carga-0270.sql` | `0806c98340329182173239896da878dd9cf70cfe` | 94913 |
| `seed/base-ficticia/carga-0271.sql` | `1f3c7eed3e8ce305741c8eb6f3c4825f329ae377` | 99928 |
| `seed/base-ficticia/carga-0272.sql` | `a39864942bdb7bdfeecc76f4344e33ef77f65640` | 67084 |
| `seed/base-ficticia/carga-0273.sql` | `fa205c65a44d2b6aff1ce0346df5c1be17f149ee` | 99854 |
| `seed/base-ficticia/carga-0274.sql` | `c52769a6deb189779ad6c7194cc4771b405142a3` | 87617 |
| `seed/base-ficticia/carga-0275.sql` | `c2699f643f60fb37d3d6225431cb77c5b0741b31` | 85969 |
| `seed/base-ficticia/carga-0276.sql` | `b5ce38f934fa2efa584ca7f86a815ff8b9964f7a` | 44714 |
| `seed/base-ficticia/carga-0277.sql` | `b71bdb08135df67792c878c66f621feb0750cedf` | 56466 |
| `seed/base-ficticia/carga-0278.sql` | `c811aeabb7e28a396174aff04a8fa02fc726cc0b` | 95918 |
| `seed/base-ficticia/carga-0279.sql` | `a96499fc5777bfd04e25bd881f90adebf4382977` | 66598 |
| `seed/base-ficticia/carga-0280.sql` | `e929e3237297c801815340119e9656b189ce2687` | 77033 |
| `seed/base-ficticia/carga-0281.sql` | `165d3ee1d10ef9a4afc4d5ec9358b9c4350f48cd` | 98664 |
| `seed/base-ficticia/carga-0282.sql` | `1cebf38d4918fb4a540c822350375379b867cb36` | 81055 |
| `seed/base-ficticia/carga-0283.sql` | `54e6abef26663f9c9409493df876194f0ec4db0f` | 63147 |
| `seed/base-ficticia/carga-0284.sql` | `24cea1fdd9bc960fd646d1ededd2d1da3ba09d95` | 54565 |
| `seed/base-ficticia/carga-0285.sql` | `c59eac1e0ca4d6fd25caa8af1fc547bbf8334161` | 99800 |
| `seed/base-ficticia/carga-0286.sql` | `74ef05d0f4d28d22a25d39818eb8f156e583abee` | 88171 |
| `seed/base-ficticia/carga-0287.sql` | `b00f751623a51d21e7d62e1a018cc641bb80dce4` | 76519 |
| `seed/base-ficticia/carga-0288.sql` | `4e1a781b481211785b2355f39a6239b383956227` | 79523 |
| `seed/base-ficticia/carga-0289.sql` | `7449145d07083a69a0e2c4580851ae26ab2acb76` | 94244 |
| `seed/base-ficticia/carga-0290.sql` | `275ab7314c4a6e008f5eed64eb1133e1bd4436e2` | 99742 |
| `seed/base-ficticia/carga-0291.sql` | `a93a6192b9c3f6ec01439d7ec290d36803cbe3b4` | 86979 |
| `seed/base-ficticia/carga-0292.sql` | `78a9c2d164604300addc8a5cdc52b72f1dee0e5c` | 76025 |
| `seed/base-ficticia/carga-0293.sql` | `4a8348dea0aa745af2a58bc5fecbdfbad81b88ad` | 37228 |
| `seed/base-ficticia/carga-0294.sql` | `bfc43cda917183643768a4a882c2398e2e44f882` | 64214 |
| `seed/base-ficticia/carga-0295.sql` | `1c387e05b104cc8e79a03fa9c6e7da84e3ccf110` | 62928 |
| `seed/base-ficticia/carga-0296.sql` | `d3e979bea07947fcd25ee4aee0cdb3e64961fd42` | 77001 |
| `seed/base-ficticia/carga-0297.sql` | `d6fe3165b640a818eb784c8afb71cd7edc0e5b5a` | 99922 |
| `seed/base-ficticia/carga-0298.sql` | `640b3a01937d9bda01a039fe7605c18d741e379c` | 60976 |
| `seed/base-ficticia/carga-0299.sql` | `c55ace5471982ee051b134198cb45aa8e70b1b7b` | 99742 |
| `seed/base-ficticia/carga-0300.sql` | `f8cdc14c9db930d0b70eae26a66b2d7482419b32` | 86979 |
| `seed/base-ficticia/carga-0301.sql` | `fe005962a446ab8e421b369ff9628d080cdf86f6` | 60396 |
| `seed/base-ficticia/carga-0302.sql` | `cf8a31bd9c98d318ede3da6980ab4d63e653c134` | 82607 |
| `seed/base-ficticia/carga-0303.sql` | `c621930be4495557bcdbfd626053e7a15484f198` | 80825 |
| `seed/base-ficticia/carga-0304.sql` | `46625f7a9bca222abe3fb43f824d97713bbf1d24` | 68646 |
| `seed/base-ficticia/carga-0305.sql` | `b19ee6ed4a7067f131ad5e279463cde1be41527f` | 76914 |
| `seed/base-ficticia/carga-0306.sql` | `cab3a83fa4dae82f9475b17393925cb75b66febf` | 99851 |
| `seed/base-ficticia/carga-0307.sql` | `964a081f82f4d0c299209a39912fc3eab2dbd93e` | 41670 |
| `seed/base-ficticia/gerar-base-ficticia.py` | `f53aa35d117f11718007b45eabfa6514067d8cb1` | 33504 |
| `seed/base-ficticia/limpeza-base-ficticia.sql` | `5c7679eb2d50df418b67c2ce03b72fce21064da9` | 3224 |
| `seed/base-ficticia/manifesto.json` | `dd9f28d46ed9c3ced321d31a4ca3ac1d3e982da8` | 45218 |
| `seed/base-ficticia/matriz.csv` | `7240c520e9585b9d4fa6ef96af70c0fb5e77a54c` | 2059 |
| `seed/base-ficticia/prova-carga-limpeza.sh` | `f5991fbed3827aa442c686255f4e02b796822cf0` | 22385 |

**Fim do Adendo R13-C.** A mesma tabela existe como ficheiro autónomo em
`correcoes-r13-c/TABELA-VERIFICACAO-POS-POUSO-BASE-2026-11-09.md`.

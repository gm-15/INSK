import streamlit as st
import pandas as pd
import numpy as np
import os
import altair as alt
import re
import asyncio

# --- 챗봇을 위한 LangChain(Gemini) 라이브러리 ---
from langchain_google_genai import GoogleGenerativeAIEmbeddings, ChatGoogleGenerativeAI
from langchain_community.vectorstores import FAISS
from langchain.chains import RetrievalQA
from langchain.prompts import PromptTemplate

# -----------------------------------------------------------------
# 페이지 기본 설정
# -----------------------------------------------------------------
st.set_page_config(layout="wide", page_title="AI 뉴스 대시보드")

# CSS 코드 수정
st.markdown("""
<style>
    /* Google Font Import */
    @import url('https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&display=swap');

    :root {
        --brand-dark: #121212;
        --brand-card: #1E1E1E;
        --brand-card-hover: #2a2a2a;
        --brand-light: #E0E0E0;
        --brand-light-secondary: #B0B0B0;
        --color-telco: #3B82F6; /* blue-500 */
        --color-llm: #22C55E; /* green-500 */
        --color-infra: #F97316; /* orange-500 */
        --color-ai-ecosystem: #A855F7; /* purple-500 */
        --color-etc: #6B7280; /* gray-500 for '기타' */
        --color-importance-high: #FBBF24; /* amber-400 */
    }

    body, .stApp {
        font-family: 'Noto Sans KR', sans-serif;
        background-color: var(--brand-dark);
        color: var(--brand-light);
    }
    
    .stRadio > div {
        display: flex;
        justify-content: center;
        gap: 20px;
    }

    .news-card {
        background-color: var(--brand-card);
        border-radius: 12px;
        padding: 24px;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        transition: transform 0.3s ease, background-color 0.3s ease;
        /* 모든 카드에 동일한 테두리 색상 적용 */
        border-left: 5px solid var(--color-importance-high); 
        height: 280px;
        margin-bottom: 20px;
        box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
    }

    .news-card:hover {
        transform: translateY(-6px);
        background-color: var(--brand-card-hover);
    }
    
    .news-card a {
        text-decoration: none;
    }

    .news-card h3 {
        color: white;
        font-size: 1.1rem;
        font-weight: bold;
        margin-bottom: 12px;
        display: -webkit-box;
        -webkit-line-clamp: 3;
        -webkit-box-orient: vertical;
        overflow: hidden;
        text-overflow: ellipsis;
        line-height: 1.4;
        min-height: 4.2em;
    }

    .news-card p {
        color: var(--brand-light-secondary);
        font-size: 0.85rem;
        display: -webkit-box;
        -webkit-line-clamp: 4;
        -webkit-box-orient: vertical;
        overflow: hidden;
        text-overflow: ellipsis;
        line-height: 1.3;
        flex-grow: 1;
        margin-bottom: 12px;
    }

    .category-tag {
        display: inline-block;
        padding: 4px 12px;
        border-radius: 9999px;
        font-size: 0.75rem;
        font-weight: 500;
        color: white;
    }

    /* 카테고리별 배경 색상 */
    .category-Telco { background-color: var(--color-telco); }
    .category-LLMAIService { background-color: var(--color-llm); }
    .category-AIInfra { background-color: var(--color-infra); }
    .category-AIEcosystem { background-color: var(--color-ai-ecosystem); }
    .category-기타 { background-color: var(--color-etc); }
    
    .insight-card {
        background-color: var(--brand-card);
        border-radius: 12px;
        padding: 20px;
        margin-bottom: 15px;
        border-left: 4px solid #3B82F6;
    }
    .insight-title {
        color: #3B82F6;
        font-size: 1.1rem;
        font-weight: bold;
        margin-bottom: 8px;
    }
    .insight-content {
        color: var(--brand-light);
        font-size: 0.9rem;
        line-height: 1.5;
    }
</style>
""", unsafe_allow_html=True)

# -----------------------------------------------------------------
# 1. 데이터 로딩
# -----------------------------------------------------------------
@st.cache_data(ttl=3600)
def load_news_from_files(file_paths):
    """
    여러 로컬 파일(엑셀, CSV)에서 뉴스 데이터를 불러와 병합하고, 중복을 제거하며 전처리합니다.
    (싱글/더블 따옴표 모두 처리하도록 개선)
    """
    all_news = []
    for file_path in file_paths:
        if not os.path.exists(file_path):
            st.warning(f"'{file_path}' 파일을 찾을 수 없습니다. 건너뜁니다.")
            continue
        try:
            try:
                df = pd.read_csv(file_path)
            except (UnicodeDecodeError, pd.errors.ParserError):
                df = pd.read_excel(file_path)

            col_map = {
                '제목': 'title', '내용 요약': 'summary', '분류 결과': 'category',
                '대분류': 'category', '소분류': 'subcat', 'URL': 'url', '총 점': 'score'
            }
            df = df.rename(columns=col_map)

            if 'summary' not in df.columns:
                st.warning(f"'{file_path}' 파일에서 필수 컬럼('내용 요약')을 찾을 수 없어 건너뜁니다.")
                continue

            if 'title' not in df.columns:
                df['title'] = df['summary'].str.split('|').str[0].str.slice(0, 50) + "..."

            if 'subcat' in df.columns:
                def parse_subcat(x):
                    if pd.isna(x) or not isinstance(x, str) or not x.strip():
                        return ''

                    processed_value = str(x).strip().replace('“', '"').replace('”', '"')

                    try:
                        # 1. 큰따옴표 패턴: "소분류" : "키워드"
                        match = re.search(r'"소분류"\s*:\s*"([^"]*)"', processed_value)
                        if match:
                            return match.group(1).strip()

                        # 2. 작은따옴표 패턴: '소분류' : '키워드'
                        match = re.search(r"'소분류'\s*:\s*'([^']*)'", processed_value)
                        if match:
                            return match.group(1).strip()

                        return '' # 패턴을 못 찾으면 빈 문자열 반환
                    except Exception:
                        return '' # 오류 발생 시 빈 문자열 반환

                df['subcat'] = df['subcat'].apply(parse_subcat)

            all_news.append(df)

        except Exception as e:
            st.error(f"'{file_path}' 파일 로딩/처리 중 오류 발생: {e}")
            continue

    if not all_news:
        return pd.DataFrame()

    merged_df = pd.concat(all_news, ignore_index=True)

    if 'title' in merged_df.columns and 'summary' in merged_df.columns:
        merged_df['title_len'] = merged_df['title'].str.len()
        merged_df.sort_values(by=['summary', 'title_len'], ascending=[True, False], inplace=True)
        merged_df.drop_duplicates(subset=['summary'], keep='first', inplace=True)
        merged_df.drop(columns=['title_len'], inplace=True)

    required_cols = ['title', 'summary', 'category', 'subcat', 'url', 'score']
    for col in required_cols:
        if col not in merged_df.columns:
            merged_df[col] = 0 if col == 'score' else ''

    merged_df = merged_df[required_cols]
    merged_df = merged_df[merged_df['summary'].astype(str).str.strip() != ''].dropna(subset=['summary'])

    def normalize_category(cat_str):
        s = str(cat_str).strip().lower()
        if "telco" in s or "통신" in s or "텔코" in s: return "Telco"
        elif "llm" in s or "service" in s or "서비스" in s: return "LLM/AI Service"
        elif "infra" in s or "인프라" in s: return "AI Infra"
        elif "ecosystem" in s or "생태계" in s: return "AI Ecosystem"
        return "기타"

    merged_df['category'] = merged_df['category'].apply(normalize_category)

    if 'summary' in merged_df.columns:
        merged_df['summary'] = merged_df['summary'].str.split('|').str[-1].str.strip()

    merged_df['score'] = pd.to_numeric(merged_df['score'], errors='coerce').fillna(0)
    merged_df['importance'] = np.where(merged_df['score'] > 0, 'High', 'Medium')

    return merged_df[['title','summary','category','subcat', 'url', 'importance']].fillna('')

# 인사이트 분석 함수 유지
def analyze_insights(df):
    """뉴스 데이터에서 인사이트를 추출합니다."""
    insights = []
    
    if not df.empty:
        category_counts = df['category'].value_counts()
        if not category_counts.empty:
            top_category = category_counts.index[0]
            insights.append({
                'title': '📊 주요 카테고리 분석',
                'content': f'가장 많이 언급된 카테고리는 "{top_category}"({category_counts.iloc[0]}개 기사)입니다.'
            })
        
        # importance_counts = df['importance'].value_counts()
        # high_ratio = (importance_counts.get('High', 0) / len(df)) * 100
        # insights.append({
        #     'title': '⭐ 중요도 분석',
        #     'content': f'전체 {len(df)}개 뉴스 중 {importance_counts.get("High", 0)}개({high_ratio:.1f}%)가 높은 중요도로 분류되었습니다. {"중요한 이슈들이 많이 발생했습니다." if high_ratio > 30 else "일반적인 뉴스 흐름을 보이고 있습니다."}'
        # })
        
        # if 'subcat' in df.columns and not df['subcat'].isna().all():
        #     subcat_df = df['subcat'].dropna().str.split(',').explode().str.strip()
        #     subcat_df = subcat_df[subcat_df != '']
        #     if not subcat_df.empty:
        #         top_keywords = subcat_df.value_counts().head(3).index.tolist()
        #         insights.append({
        #             'title': '🔍 핵심 키워드 트렌드',
        #             'content': f'가장 주목받는 키워드는 "{", ".join(top_keywords)}"입니다. 이들 키워드를 중심으로 한 뉴스가 활발히 보도되고 있습니다.'
        #         })
        
        # title_lengths = df['title'].str.len()
        # avg_length = title_lengths.mean()
        # insights.append({
        #     'title': '📝 뉴스 제목 특성',
        #     'content': f'뉴스 제목의 평균 길이는 {avg_length:.0f}자입니다. {"상세한 정보를 담은 긴 제목들이 많습니다." if avg_length > 50 else "간결한 제목들이 주를 이루고 있습니다."}'
        # })
    
    return insights

# ⭐️ [수정] 파일 경로를 .csv로 다시 수정
FILE_PATHS = ["final_insk03.xlsx", "final_insk02.xlsx"]
news_df = load_news_from_files(FILE_PATHS)


# -----------------------------------------------------------------
# 2. RAG 파이프라인 구축 (기존 코드 유지)
# -----------------------------------------------------------------
@st.cache_resource
def build_rag_pipeline(df):
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)

    if df.empty:
        return None
    documents = [f"제목: {row['title']}\n\n내용: {row['summary']}" for i, row in df.iterrows()]
    try:
        google_api_key = st.secrets.get("google", {}).get("api_key")
        if not google_api_key:
            st.error("Gemini API 키가 secrets.toml에 설정되지 않았습니다.")
            return None

        embeddings = GoogleGenerativeAIEmbeddings(model="models/embedding-001", google_api_key=google_api_key)
        vectorstore = FAISS.from_texts(texts=documents, embedding=embeddings)
        
        llm = ChatGoogleGenerativeAI(
            model="gemini-1.5-flash",
            temperature=0.3,
            google_api_key=google_api_key
        )
        
        prompt_template = """당신은 AI 뉴스 전문가입니다. '참고 기사'를 바탕으로 '질문'에 대해 한국어로 답변해주세요.
        [참고 기사]
        {context}
        
        [질문]
        {question}
        
        [답변]
        """
        PROMPT = PromptTemplate(template=prompt_template, input_variables=["context", "question"])

        qa_chain = RetrievalQA.from_chain_type(
            llm=llm,
            chain_type="stuff",
            retriever=vectorstore.as_retriever(search_kwargs={"k": 3}),
            chain_type_kwargs={"prompt": PROMPT},
            return_source_documents=True
        )
        return qa_chain
    except Exception as e:
        st.error(f"챗봇 파이프라인 구축 오류: {e}")
        return None

# -----------------------------------------------------------------
# 3. 메인 UI 및 챗봇 로직
# -----------------------------------------------------------------
st.title("📰 AI 뉴스 대시보드")
if news_df.empty:
    st.warning("데이터를 불러올 수 없습니다. 엑셀/CSV 파일 경로를 확인해주세요.")
    st.stop()

tab1, tab2, tab3, tab4 = st.tabs(["📈 뉴스 대시보드", "📊 트렌드 분석", "🤖 AI 뉴스 챗봇", "💡 인사이트 분석"])

with tab1:
    st.header("📰 최신 뉴스 목록")
    
    FIXED_CATEGORIES = ["All", "Telco", "LLM/AI Service", "AI Infra", "AI Ecosystem", "기타"]
    
    selected_category = st.radio("카테고리 선택", FIXED_CATEGORIES, horizontal=True, index=0)

    if selected_category == "All":
        filtered_df = news_df
    else:
        filtered_df = news_df[news_df['category'] == selected_category]

    st.write("---")
    st.write(f"총 {len(filtered_df)}개의 뉴스")

    if filtered_df.empty:
        st.info("해당 카테고리에 뉴스가 없습니다.")
    else:
        cols = st.columns(3)
        for idx, (_, row) in enumerate(filtered_df.iterrows()):
            category_name = str(row['category']).strip()
            category_class_name = ''.join(e for e in category_name if e.isalnum())
            
            # color_hash = abs(hash(category_name)) % (256*256*256)
            # r = max(80, (color_hash & 0xFF0000) >> 16)
            # g = max(80, (color_hash & 0x00FF00) >> 8)
            # b = max(80, color_hash & 0x0000FF)
            
            # st.markdown(f"""
            # <style>
            #     .category-{category_class_name} {{ 
            #         background-color: rgb({r}, {g}, {b}) !important;
            #         opacity: 0.9;
            #     }}
            # </style>
            # """, unsafe_allow_html=True)

            with cols[idx % 3]:
                url_link = row['url'] if row['url'] and isinstance(row['url'], str) and row['url'].strip() else "#"
                target = 'target="_blank"' if url_link != "#" else ''
                
                st.markdown(f"""
                <a href="{url_link}" {target} style="text-decoration: none;">
                    <div class="news-card importance-{row['importance']}">
                        <div>
                            <h3>{row['title']}</h3>
                            <p>{row['summary']}</p>
                        </div>
                        <div style="margin-top: auto; display:flex; justify-content:space-between; align-items:center;">
                            <span class="category-tag category-{category_class_name}">{category_name}</span>
                            <span style="font-size:0.875rem; font-weight:500; color:var(--color-importance-{row['importance'].lower()})">{row['importance']}</span>
                        </div>
                    </div>
                </a>
                """, unsafe_allow_html=True)

# ⭐️ [수정] 트렌드 분석 탭을 완전히 다시 작성
with tab2:
    st.header("📊 키워드 트렌드")
    
    # 디버깅을 위한 정보 표시
    st.info(f"전체 데이터 수: {len(news_df)}")
    
    # subcat 컬럼 존재 여부 확인
    if 'subcat' in news_df.columns:
        
        
        # 비어있지 않은 subcat 데이터 확인
        non_empty_subcat = news_df['subcat'].dropna()
        non_empty_subcat = non_empty_subcat[non_empty_subcat.astype(str).str.strip() != '']
        
        
        
        if len(non_empty_subcat) > 0:
            # 샘플 데이터 표시
            st.subheader("샘플 subcat 데이터:")
            sample_data = non_empty_subcat.head(5).tolist()
            for i, sample in enumerate(sample_data, 1):
                st.text(f"{i}. {sample}")
            
            # 키워드 추출 및 분석
            try:
                # 콤마로 분리하고 각 키워드를 개별 항목으로 변환
                subcat_series = non_empty_subcat.str.split(',').explode()
                # 앞뒤 공백 제거
                subcat_series = subcat_series.str.strip()
                # 빈 문자열 제거
                subcat_series = subcat_series[subcat_series != '']
                
                st.info(f"추출된 개별 키워드 수: {len(subcat_series)}")
                
                if len(subcat_series) > 0:
                    # 키워드 빈도 계산
                    keyword_counts = subcat_series.value_counts().reset_index()
                    keyword_counts.columns = ['keyword', 'count']
                    
                    st.info(f"고유 키워드 수: {len(keyword_counts)}")
                    
                    # 상위 키워드 선택 슬라이더
                    max_keywords = min(20, len(keyword_counts))
                    if max_keywords > 0:
                        top_n = st.slider("상위 키워드 개수", 1, max_keywords, value=min(10, max_keywords))
                        plot_data = keyword_counts.head(top_n)
                        
                        # 차트 생성
                        try:
                            chart = alt.Chart(plot_data).mark_bar(
                                cornerRadius=5,
                                height=25
                            ).encode(
                                x=alt.X('count:Q', 
                                       title='언급 횟수',
                                       axis=alt.Axis(labelColor='white', titleColor='white', grid=True)),
                                y=alt.Y('keyword:N', 
                                       title='소분류 키워드', 
                                       sort='-x',
                                       axis=alt.Axis(labelColor='white', titleColor='white')),
                                color=alt.Color('count:Q', 
                                              legend=None, 
                                              scale=alt.Scale(scheme='blues')),
                                tooltip=['keyword:N', 'count:Q']
                            ).properties(
                                title=alt.TitleParams(
                                    text=f"상위 {top_n}개 소분류 키워드 언급 빈도",
                                    color='white',
                                    fontSize=16,
                                    anchor='start'
                                ),
                                height=max(400, top_n * 30),
                                width=700
                            ).configure_view(
                                strokeWidth=0
                            ).configure_axis(
                                gridColor='#444444'
                            ).resolve_scale(
                                color='independent'
                            )

                            st.altair_chart(chart, use_container_width=True)
                            
                            # 상위 키워드 테이블도 표시
                            st.subheader("상위 키워드 상세 정보")
                            st.dataframe(plot_data, use_container_width=True)
                            
                        except Exception as chart_error:
                            st.error(f"차트 생성 중 오류 발생: {chart_error}")
                            # 대신 간단한 바 차트 표시
                            st.bar_chart(plot_data.set_index('keyword')['count'])
                    
                    else:
                        st.warning("표시할 키워드가 없습니다.")
                else:
                    st.warning("키워드 추출 후 데이터가 비어있습니다.")
                    
            except Exception as processing_error:
                st.error(f"키워드 처리 중 오류 발생: {processing_error}")
                st.text("오류 세부 정보:")
                st.text(str(processing_error))
        else:
            
            
            # 전체 subcat 컬럼의 상태 확인
            total_rows = len(news_df)
            null_count = news_df['subcat'].isnull().sum()
            empty_count = (news_df['subcat'].astype(str).str.strip() == '').sum()
            
            
            
            
            for i in range(min(5, len(news_df))):
                subcat_value = news_df.iloc[i]['subcat']
            
            # Excel 파일에서 직접 읽은 원본 데이터도 표시
            st.subheader("Excel 원본 데이터 재확인:")
            try:
                # Excel 파일 직접 읽기
                for file_path in FILE_PATHS:
                    if os.path.exists(file_path):
                        st.write(f"파일: {file_path}")
                        raw_df = pd.read_excel(file_path)
                        
                        # 소분류 관련 컬럼 찾기
                        subcat_cols = [col for col in raw_df.columns if '소분류' in col]
                        if subcat_cols:
                            st.write(f"소분류 컬럼: {subcat_cols}")
                            for col in subcat_cols:
                                st.write(f"컬럼 '{col}'의 첫 3개 데이터:")
                                for idx, val in enumerate(raw_df[col].head(3)):
                                    st.code(f"  {idx+1}: {repr(val)}")
                        break
            except Exception as e:
                st.error(f"Excel 원본 확인 중 오류: {e}")
            
            # 대안: 다른 컬럼을 이용한 키워드 분석 제안
            # st.subheader("🔄 대안 분석:")
            # st.info("subcat 데이터가 비어있으므로, 제목이나 요약에서 키워드를 추출해보겠습니다.")
            
            # 제목에서 키워드 추출 시도
            if 'title' in news_df.columns:
                titles = news_df['title'].dropna().astype(str)
                if len(titles) > 0:
                    # 간단한 키워드 추출 (3글자 이상의 단어)
                    import re
                    all_words = []
                    for title in titles:
                        # 한글, 영문, 숫자만 추출
                        words = re.findall(r'[가-힣a-zA-Z0-9]{3,}', title)
                        all_words.extend(words)
                    
                    if all_words:
                        word_series = pd.Series(all_words)
                        word_counts = word_series.value_counts().head(10)
                        
                        st.subheader("제목에서 추출한 키워드 TOP 10:")
                        st.bar_chart(word_counts)
                        
                        # 테이블로도 표시
                        word_df = word_counts.reset_index()
                        word_df.columns = ['키워드', '빈도']
                        st.dataframe(word_df)
    else:
        st.error("'subcat' 컬럼이 데이터에 존재하지 않습니다.")
        st.subheader("사용 가능한 컬럼:")
        st.write(list(news_df.columns))
        
        # 만약 다른 이름의 소분류 컬럼이 있다면 표시
        possible_subcat_cols = [col for col in news_df.columns if '소분류' in col or 'sub' in col.lower()]
        if possible_subcat_cols:
            st.subheader("소분류 관련 가능한 컬럼:")
            st.write(possible_subcat_cols)

with tab3:
    st.header("🤖 AI 뉴스 분석 챗봇")
    st.info("수집된 뉴스 데이터를 기반으로 질문에 답변합니다. (Gemini 사용)")

    qa_chain = build_rag_pipeline(news_df)

    if qa_chain:
        if "messages" not in st.session_state:
            st.session_state.messages = [{"role": "assistant", "content": "안녕하세요! 수집된 뉴스에 대해 무엇이든 물어보세요."}]

        for message in st.session_state.messages:
            with st.chat_message(message["role"]):
                st.markdown(message["content"])

        if prompt := st.chat_input("예: 'MS 관련 최근 동향 요약해줘'"):
            st.session_state.messages.append({"role": "user", "content": prompt})
            with st.chat_message("user"):
                st.markdown(prompt)

            with st.chat_message("assistant"):
                with st.spinner("답변을 생성 중입니다..."):
                    try:
                        result = qa_chain.invoke({"query": prompt})
                        response = result["result"]
                        source_docs = result["source_documents"]
                        
                        st.markdown(response)

                        with st.expander("참고한 뉴스 보기"):
                            for doc in source_docs:
                                st.markdown(f"- {doc.page_content.split('내용:')[0].strip()}")

                    except Exception as e:
                        response = f"답변 생성 중 오류가 발생했습니다: {e}"
                        st.error(response)
            
            st.session_state.messages.append({"role": "assistant", "content": response})

with tab4:
    st.header("💡 데이터 인사이트 분석")
    st.info("수집된 뉴스 데이터를 기반으로 한 주요 인사이트입니다.")
    
    insights = analyze_insights(news_df)
    
    if insights:
        for insight in insights:
            st.markdown(f"""
            <div class="insight-card">
                <div class="insight-title">{insight['title']}</div>
                <div class="insight-content">{insight['content']}</div>
            </div>
            """, unsafe_allow_html=True)
        
        st.subheader("📊 추가 통계 정보")
        col1, col2, col3 = st.columns(3)
        with col1:
            st.metric("전체 뉴스 수", len(news_df))
        with col2:
            categories_count = len(news_df['category'].unique())
            st.metric("카테고리 수", categories_count)
        with col3:
            high_importance = len(news_df[news_df['importance'] == 'High'])
            st.metric("중요 뉴스 수", high_importance)
        
        st.subheader("📋 상세 데이터")
        with st.expander("전체 데이터 보기"):
            st.dataframe(news_df, use_container_width=True)
    else:
        st.warning("인사이트를 생성할 데이터가 부족합니다.")




